package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbmCloudIngressApiTest {
    final ObjectMapper json = new ObjectMapper();
    final IbmCloudIngressApi api = spy(new IbmCloudIngressApi());
    final IbmCloudIngressApi.Session session = new IbmCloudIngressApi.Session("test-token",URI.create("https://containers.cloud.ibm.com/global/"),URI.create("https://jp-osa.iaas.cloud.ibm.com"));
    @TempDir Path directory;

    @Test void officialGetAndPatchContractsAndNoRawKeyInSession() throws Exception {
        var profile = new IbmIngressAutomationProperties.Profile(); profile.setRegion("jp-osa");
        Path key=directory.resolve("api-key"); Files.writeString(key,"private-key-for-test"); profile.setApiKeyFile(key.toString());
        doReturn(json.readTree("{\"access_token\":\"test-token\"}")).when(api).request(eq("POST"),any(),isNull(),anyString(),eq("application/x-www-form-urlencoded"));
        var login=api.login(profile);
        assertThat(login.toString()).doesNotContain("test-token","private-key-for-test");
        assertThat(login.containers().toString()).endsWith("/global/");
        doReturn(json.readTree("{\"id\":\"cluster-id\",\"provider\":\"vpc-gen2\",\"proxyProtocol\":{\"enable\":false}}")).when(api).request(eq("GET"),any(),any(),isNull(),any());
        api.cluster(login,"cluster-id"); api.proxyConfig(login,"cluster-id","public");
        verify(api).request(eq("GET"),eq(URI.create("https://containers.cloud.ibm.com/global/ingress/v2/load-balancer/configuration?cluster=cluster-id&type=public")),eq("test-token"),isNull(),any());
        doReturn(json.createObjectNode()).when(api).request(eq("PATCH"),any(),any(),any(),any());
        api.enableProxy(login,"cluster-id",List.of("10.2.0.0/24"));
        var body=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(api).request(eq("PATCH"),eq(URI.create("https://containers.cloud.ibm.com/global/ingress/v2/load-balancer/configuration")),eq("test-token"),body.capture(),eq("application/json"));
        assertThat(json.readTree(body.getValue()).path("proxyProtocol").path("cidr").get(0).asText()).isEqualTo("10.2.0.0/24");
        assertThat(json.readTree(body.getValue()).path("proxyProtocol").path("enable").asBoolean()).isTrue();
    }

    void lbResponses(String vpc, int capacity, String cidr, String next) throws Exception {
        doAnswer(call -> {
            URI uri=call.getArgument(1);
            if (uri.getPath().equals("/v1/load_balancers")) return json.readTree("""
                    {"load_balancers":[{"hostname":"a.lb.appdomain.cloud","provisioning_status":"active","subnets":[{"id":"subnet-1"}]},
                    {"hostname":"b.lb.appdomain.cloud","provisioning_status":"active","subnets":[{"id":"subnet-1"}]}],"next":{"href":"%s"}}
                    """.formatted(next));
            return json.readTree("{\"vpc\":{\"id\":\""+vpc+"\"},\"available_ipv4_address_count\":"+capacity+",\"ipv4_cidr_block\":\""+cidr+"\"}");
        }).when(api).request(eq("GET"),any(),any(),isNull(),any());
    }
    @Test void discoversExactLbSubnetsAndCountsCapacityAcrossSharedLbs() throws Exception {
        lbResponses("vpc-1",4,"10.2.0.0/24","");
        assertThat(api.trustedSubnets(session,json.readTree("{\"vpcs\":[\"vpc-1\"]}"),Set.of("a.lb.appdomain.cloud","b.lb.appdomain.cloud"),true)).containsExactly("10.2.0.0/24");
    }
    @ParameterizedTest @ValueSource(ints={-1,0,1,2,3}) void insufficientCapacityStopsBeforePatch(int capacity) throws Exception {
        lbResponses("vpc-1",capacity,"10.2.0.0/24","");
        assertThatThrownBy(() -> api.trustedSubnets(session,json.readTree("{\"vpcs\":[\"vpc-1\"]}"),Set.of("a.lb.appdomain.cloud","b.lb.appdomain.cloud"),true)).hasMessageContaining("Insufficient");
        verify(api,never()).request(eq("PATCH"),any(),any(),any(),any());
    }
    @Test void rejectsUnmatchedLoadBalancerAndForeignVpc() throws Exception {
        lbResponses("vpc-other",100,"10.2.0.0/24","");
        var cluster=json.readTree("{\"vpcs\":[\"vpc-1\"]}");
        assertThatThrownBy(() -> api.trustedSubnets(session,cluster,Set.of("missing.lb.appdomain.cloud"),true)).hasMessageContaining("Cannot match");
        assertThatThrownBy(() -> api.trustedSubnets(session,cluster,Set.of("a.lb.appdomain.cloud"),true)).hasMessageContaining("outside");
    }
    @ParameterizedTest @ValueSource(strings={"https://evil.example/v1/load_balancers","http://jp-osa.iaas.cloud.ibm.com/v1/load_balancers","https://jp-osa.iaas.cloud.ibm.com/other"})
    void neverSendsBearerTokenToUntrustedPagination(String next) throws Exception {
        lbResponses("vpc-1",10,"10.2.0.0/24",next);
        assertThatThrownBy(() -> api.trustedSubnets(session,json.readTree("{\"vpcs\":[\"vpc-1\"]}"),Set.of("a.lb.appdomain.cloud"),true)).hasMessageContaining("pagination");
        verify(api,never()).request(eq("GET"),eq(URI.create(next)),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(ints={301,401,403,429,500}) void httpFailuresDoNotExposeCredentialsOrUpstreamBody(int status) throws Exception {
        HttpClient http=mock(HttpClient.class); HttpResponse<String> response=mock(HttpResponse.class);
        when(http.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn("private upstream key data");
        var real=new IbmCloudIngressApi(http);
        assertThatThrownBy(() -> real.request("PATCH",URI.create("https://containers.cloud.ibm.com/global/test"),"secret-token","secret-body","application/json"))
                .hasMessageContaining("HTTP "+status).hasMessageNotContaining("private upstream")
                .hasMessageNotContaining("secret-token").hasMessageNotContaining("secret-body");
    }
    @ParameterizedTest @ValueSource(strings={"", "../another-cluster", "https://bad", "cluster?x=y", "cluster\nheader"})
    void invalidIdsNeverReachNetwork(String id) {
        assertThatThrownBy(() -> api.cluster(session,id)).isInstanceOf(IllegalArgumentException.class);
        verify(api,never()).request(any(),any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"proxyProtocol\":{}}","{\"proxyProtocol\":{\"enable\":\"false\"}}"})
    void unknownProxyResponseIsNotTreatedAsDisabled(String response) throws Exception {
        doReturn(json.readTree(response)).when(api).request(eq("GET"),any(),any(),isNull(),any());
        assertThatThrownBy(() -> api.proxyConfig(session,"cluster-id","public")).hasMessageContaining("unknown PROXY");
        verify(api,never()).request(eq("PATCH"),any(),any(),any(),any());
    }
}
