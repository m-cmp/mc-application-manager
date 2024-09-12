<template> 
  <div class="modal" id="modal-hpa" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">HPA</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <div class="card">
            <div class="card-body">
              <h4>YAML</h4>
              <div>
                <pre>
                  apiVersion: apps/v1
                  kind: Deployment
                  metadata:
                  name: nginx-deployment
                  labels:
                  app: nginx
                  spec: replicas: 3
                  selector: matchLabels:
                  app: nginx
                  template: metadata:
                  labels:
                    app: nginx
                    spec:
                      containers:
                      - name: nginx
                        image: nginx:1.14.2
                        ports:
                        - containerPort: 80
                </pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';

 onMounted(async () => {
})

</script>