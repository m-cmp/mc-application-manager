package kr.co.mcmp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

public class SSHManager {
	private static Logger log = LoggerFactory.getLogger(SSHManager.class);
	
	private String ip;
	private int port;
	private String username;
	private String password;
	private Session session;
	

	public SSHManager(String ip, int port, String username,  String password) {
		this.ip = ip;
		this.port = port;
		this.username = username;
		this.password = password;
	}
	
	public void connect() throws JSchException {
		log.info("connecting... "+ip);
		
		JSch jsch = new JSch();
		this.session = jsch.getSession(username, ip, port);
		this.session.setPassword(password);
		
		Properties config = new Properties();
		config.put("StrictHostKeyChecking", "no");
		
		this.session.setConfig(config);
		this.session.connect();
	}
	
	public void upload(File file, String remoteDir, SftpProgressMonitor monitor) throws Exception {
		upload(new File[] {file}, remoteDir, monitor);
	}
	
	public void upload(File[] files, String remoteDir, SftpProgressMonitor monitor) throws Exception {
		for(File f : files) {
			ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
			
			sftpChannel.connect(10000);
			mkdirs(sftpChannel, remoteDir);
			
			sftpChannel.cd(remoteDir);
			FileInputStream fis = new FileInputStream(f);
			
			if(monitor != null) {
				sftpChannel.put(fis, f.getName(), monitor);
			} else {
				sftpChannel.put(fis, f.getName());
			}
			
			fis.close();
			sftpChannel.disconnect();
		}
	}
	
	private void mkdirs(ChannelSftp sftpChannel, String path) {
		String[] sgmt = path.split("/");
		StringBuffer sb = new StringBuffer();
		for(String s : sgmt) {
			if(s.length() > 0) {
				sb.append("/");
				sb.append(s);
				try{
					sftpChannel.mkdir(sb.toString());
			    } catch (SftpException e) {
			    	log.info(String.format("Remote directory already created. Path: %s", sb.toString()));
			    }
			}			
		}
	}
	
	public void exec(String command) throws Exception {
		exec(new String[] {command});
	}
	
	public void exec(String[] commands) throws Exception {
		for(String command : commands) {
			ChannelExec execChannel =(ChannelExec) session.openChannel("exec");
			execChannel.setPty(true);
			
			execChannel.setCommand(command);
			
			InputStream inputStream = execChannel.getInputStream();
			execChannel.connect(10000);
			
			String output = "";
			byte[] buf = new byte[1024];
			int length;
			while((length=inputStream.read(buf)) != -1) {
				output += new String(buf, 0, length);
			}
			inputStream.close();
			System.out.println(output);
			execChannel.disconnect();
		}
	}
	
	public void disconnect() {
        if(session.isConnected()){
        	log.info("disconnecting... "+ip);
            session.disconnect();
        }
    }

	public void fileUpload(File tempFile, String serverFilepath, String filename) throws Exception {
		connect();
		// 6. sftp 채널을 연다.
		Channel channel = session.openChannel("sftp");
		// 7. 채널에 연결한다.
		channel.connect();
		// 8. 채널을 FTP용 채널 객체로 캐스팅한다.
		ChannelSftp sftpChannel = (ChannelSftp) channel;
		FileInputStream fis = null;
		try {
			// Change to output directory
			sftpChannel.cd(serverFilepath);

			// Upload file
			//File file = new File("src/main/resources/application.yml");
			// 입력 파일을 가져온다.
			fis = new FileInputStream(tempFile);
			// 파일을 업로드한다.
			sftpChannel.put(fis, filename);

			fis.close();
			System.out.println("File uploaded successfully");

		} catch (Exception e) {
			e.printStackTrace();
		}

		if(session.isConnected()){
			System.out.println("disconnecting...");
			sftpChannel.disconnect();
			channel.disconnect();
			session.disconnect();
		}
	}

	public void fileDownload(String downFile, String serverFilepath) throws Exception {
		connect();
		Channel channel = session.openChannel("sftp");
		channel.connect();
		ChannelSftp sftpChannel = (ChannelSftp) channel;
		FileInputStream fis = null;
		try {
			sftpChannel.get(serverFilepath, downFile);
			System.out.println("File Download successfully");

		} catch (Exception e) {
			e.printStackTrace();
		}

		if(session.isConnected()){
			System.out.println("disconnecting...");
			sftpChannel.disconnect();
			channel.disconnect();
			session.disconnect();
		}
	}
	

	public String commandExec(String command) throws Exception {
		String result = "";
		connect();
		// 6. sftp 채널을 연다.
		Channel channel = session.openChannel("exec");
		
		ChannelExec channelExec = (ChannelExec) channel; //명령 전송 채널사용
		channelExec.setPty(true);
		try {
			channelExec.setCommand(command);
			System.out.println("### command : " + command);
			//콜백을 받을 준비.
			StringBuilder outputBuffer = new StringBuilder();
			InputStream in = channel.getInputStream();
			((ChannelExec) channel).setErrStream(System.err);
			
			channel.connect(); //실행
			
			boolean doYn = true;
			byte[] tmp = new byte[1024];

			while (doYn) {
				TimeUnit.SECONDS.sleep(2);
				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					outputBuffer.append(new String(tmp, 0, i));
					if (i < 0) break;
				}

				if (channel.isClosed()) {
					System.out.println("### result : "+outputBuffer.toString());
					result = outputBuffer.toString();
					doYn = false;
				}else{
					System.out.println("disconnecting...");
					channel.disconnect();
					session.disconnect();
				}
			}
			System.out.println("### Command Excute successfully");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
}
