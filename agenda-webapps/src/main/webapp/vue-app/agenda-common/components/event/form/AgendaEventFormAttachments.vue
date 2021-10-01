<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <v-list>
    <agenda-event-form-attachment-item 
      v-for="attachment in attachments"
      :key="attachment.id || attachment.uploadId"
      :attachment="attachment"
      :upload-progress="attachment && attachment.control && attachment.control.uploadProgress"
      @deleteFile="deleteFile" />
    <v-list-item>
      <a
        v-if="attachments.length < maxFilesCount"
        class="text-subtitle-1 font-weight-regular attach-file-link"
        @click="openUploadWindow">
        {{ $t('agenda.attachFile') }}
      </a>
      <div class="hidden">
        <input
          ref="uploadInput"
          class="file"
          name="file"
          type="file"
          multiple="multiple"
          style="display:none"
          @change="handleFileUpload($refs.uploadInput.files)">
      </div>
    </v-list-item>
    <v-list-item v-if="errors.length" class="flex-column">
      <v-alert
        v-for="(error, index) in errors"
        :key="index"
        type="error">
        {{ error }}
      </v-alert>
    </v-list-item>
  </v-list>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data: () => ({
    maxFilesCount: 5, // TODO: get from settings
    maxFileSize: 10, // TODO: get from settings
    errors: [],
  }),
  computed: {
    attachments() {
      return this.event.attachments || [];
    },
  },
  methods: {
    openUploadWindow(){
      this.$refs.uploadInput.click();
    },
    handleFileUpload: function(files) {
      if (!files || !files.length) {
        return;
      }
      const maxFileSizeInBytes = this.maxFileSize * 1024 * 1024;
      for (const file of files) {
        if (this.event.attachments.length >= this.maxFilesCount) {
          const error = this.$t('agenda.maxFilesAttachementsExceeded', {0: this.maxFilesCount, 1: file.name});
          this.addError(error);
          continue;
        }
        if (file.size > maxFileSizeInBytes) {
          const error = this.$t('agenda.maxFileSizeExceeded', {0: file.name,1: this.maxFileSize});
          this.addError(error);
          continue;
        }
        const uploadId = `${Math.round(Math.random() * 1000)}-${Date.now()}`;
        const attachment = {
          name: file.name,
          size: file.size,
          mimeType: file.type,
          uploadId,
        };
        this.event.attachments.push(attachment);

        const controller = new AbortController();
        const signal = controller.signal;
        attachment.control = {
          uploaded: false,
          uploadId,
          file,
          controller,
          signal,
        };
        attachment.control.uploadProgress = 0;
        this.uploadFile(attachment);
      }
    },
    deleteFile(attachment) {
      const index = this.event.attachments.indexOf(attachment);
      if (index >= 0) {
        this.event.attachments.splice(index, 1);

        if (attachment.control && attachment.control.uploadProgress < 100 && attachment.control.controller) {
          attachment.control.controller.abort();
          this.$uploadService.deleteUpload(attachment.uploadId);
        }
      }
    },
    uploadFile(attachment){
      const attachmentControl = attachment.control;
      attachmentControl.uploaded = true;
      this.$uploadService.upload(attachmentControl.file, attachmentControl.uploadId, attachmentControl.signal)
        .catch(e => {
          this.addError(e);
          this.deleteFile(attachment);
        });
      this.controlUpload(attachment);
    },
    controlUpload(attachment){
      window.setTimeout(() => {
        this.$uploadService.getUploadProgress(attachment.uploadId)
          .then(percent => {
            attachment.control.uploadProgress = Number(percent);
            if (attachment.control.uploadProgress < 100) {
              this.controlUpload(attachment);
            } else {
              window.setTimeout(() => {
                delete attachment.control;
              }, 1000);
            }
            this.$forceUpdate();
          })
          .catch(e => {
            this.addError(e);
            this.deleteFile(attachment);
          });
      }, 200);
    },
    addError(error){
      this.errors.push(String(error));
      window.setTimeout(() => {
        const index = this.errors.indexOf(error);
        if (index >= 0) {
          this.errors.splice(index, 1);
        }
      }, 5000);
    },
  }
};
</script>