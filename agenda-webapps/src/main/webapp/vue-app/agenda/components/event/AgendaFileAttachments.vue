<template>
  <div>
    <div ref="DropFileBox">
      <a class="text-subtitle-1 font-weight-regular attach-file-link" @click="uploadFile">{{ $t('agenda.attachFile') }}</a>
    </div>
    <div class="fileHidden" style="display:none">
      <input
        ref="uploadInput"
        class="file"
        name="file"
        type="file"
        multiple="multiple"
        style="display:none"
        @change="handleFileUpload($refs.uploadInput.files)">
    </div>
  </div>
</template>

<script>
export default {
  props: {
    attachmentsFile: {
      type: Array,
      default: null
    },
  },
  data() {
    return {
      pathDestinationFolder : '',
      filesCountLimitError: false,
      fileSizeLimitError: false,
      maxFilesCount: parseInt(`${eXo.env.portal.maxToUpload}`),
      maxFileSize: parseInt(`${eXo.env.portal.maxFileSize}`),
      uploadingCount : 0,
      sameFileError: false,
      maxUploadInProgressCount : 2,
      uploadingFilesQueue: [],
      sameFileErrorMessage: `${this.$t('attachments.agenda.sameFile.error')}`,
      BYTES_IN_MB: 1048576,
    };
  },
  methods: {
    uploadFile(){
      this.$refs.uploadInput.click();
    },
    handleFileUpload: function(files) {
      const newFilesArray = Array.from(files);
      newFilesArray.sort(function(file1, file2) {
        return file1.size - file2.size;
      });
      const newAttachedFiles = [];
      newFilesArray.forEach(file => {
        newAttachedFiles.push({
          originalFileObject: file,
          name: file.name,
          size: file.size,
          mimetype: file.type,
          uploadId: this.getNewUploadId(),
          uploadProgress: 0,
          destinationFolder: this.pathDestinationFolder,
          pathDestinationFolderForFile:'',
          isPublic: true
        });
      });
      newAttachedFiles.forEach(newFile => {
        this.queueUpload(newFile);
      });
      this.$refs.uploadInput.attachmentsFile = '';
    },
    getNewUploadId: function() {
      const maxUploadId = 100000;
      return Math.floor(Math.random() * maxUploadId);
    },
    queueUpload: function(file) {
      if(this.attachmentsFile.length >= this.maxFilesCount) {
        this.filesCountLimitError = true;
        return;
      }
      const fileSizeInMb = file.size / this.BYTES_IN_MB;
      if(fileSizeInMb > this.maxFileSize) {
        this.fileSizeLimitError = true;
        return;
      }
      const fileExists = this.attachmentsFile.some(f => f.name === file.name);
      if (fileExists) {
        this.sameFileErrorMessage = this.sameFileErrorMessage.replace('{0}', file.name);
        this.sameFileError = true;
        return;
      }
      this.attachmentsFile.push(file);
      this.$emit('files',this.attachmentsFile);
      if(this.uploadingCount < this.maxUploadInProgressCount) {
        this.sendFileToServer(file);
      } else {
        this.uploadingFilesQueue.push(file);
      }
    },
    sendFileToServer(){
      // TODO
    }
  }
};
</script>