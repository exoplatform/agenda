<template>
  <v-list-item class="d-flex flex-row">
    <i :class="getIconClassFromFileMimeType"></i>
    <div class="d-flex flex-column ml-2">
      <a :href="attachment.url || ''" class="my-auto">{{ attachment.name }} ({{ formattedFileSize }})</a>
      <v-progress-linear
        v-if="attachment.control && uploadProgress < 100"
        :value="uploadProgress"
        class="my-auto" />
    </div>
    <v-btn
      color="grey"
      icon
      dark
      @click="$emit('deleteFile', attachment)">
      <v-icon>
        mdi-close
      </v-icon>
    </v-btn>
  </v-list-item>
</template>

<script>
export default {
  props: {
    attachment: {
      type: Object,
      default: null,
    },
    uploadProgress: {
      type: Number,
      default: null,
    },
  },
  data: () => ({
    megabyte: 1024 * 1024,
    kilobyte: 1024,
  }),
  computed: {
    formattedFileSize() {
      if (this.attachment.size < this.megabyte) {
        const size = Number(this.attachment.size / this.kilobyte).toFixed(2);
        return this.$t('agenda.sizeInKB', {0: size});
      } else {
        const size = Number(this.attachment.size / this.megabyte).toFixed(2);
        return this.$t('agenda.sizeInMB', {0: size});
      }
    },
    getIconClassFromFileMimeType() {
      if(this.attachment) {
        const fileMimeTypeClass = this.attachment.mimeType.replace(/\./g, '').replace('/', '').replace('\\', '');
        return this.attachment.mimeType
          ? `uiIcon32x32${this.attachment.mimeType.replace(/[/.]/g, '')}`
          : `uiIconFileType${fileMimeTypeClass} uiIconFileTypeDefault`;
      } else {
        return '';
      }
    },
  },
};
</script>