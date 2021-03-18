<template>
  <v-expand-transition>
    <div v-show="visible">
      <textarea 
        id="datePollComment"
        ref="editor"
        :placeholder="$t('agenda.commentDatePollPlaceholder')"
        cols="30"
        rows="10"
        class="textarea"
        autofocus></textarea>
      <div 
        :class="messageExceedsLength && 'error--text'"
        class="datePollMessageLength d-flex">
        <span v-if="messageExceedsLength">
          {{ limitMessageLabel }}
        </span>
        <span class="ml-auto">{{ messageLength }} / {{ maxLength }}</span>
      </div>
      <v-btn
        :disabled="cantComment"
        class="btn my-2"
        @click="saveComment">
        {{ $t('agenda.button.comment') }}
      </v-btn>
    </div>
  </v-expand-transition>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    message: null,
    editorReady: false,
    visible: false,
    maxLength: 1000,
  }),
  computed: {
    messageLength() {
      return this.message && this.message.length || 0;
    },
    messageExceedsLength() {
      return this.messageLength > this.maxLength;
    },
    limitMessageLabel() {
      return this.$t('textarea.limitMessage').replace('{0}', this.maxLength);
    },
    cantComment() {
      return this.messageExceedsLength || !this.messageLength;
    },
    activityId() {
      return this.event && this.event.activityId;
    },
  },
  mounted() {
    this.initCKEditor();
  },
  methods: {
    initCKEditor: function () {
      if (CKEDITOR.instances['datePollComment']) {
        CKEDITOR.instances['datePollComment'].destroy(true);
      }
      const extraPlugins = 'suggester,widget,embedsemantic';
      // this line is mandatory when a custom skin is defined
      CKEDITOR.basePath = '/commons-extension/ckeditor/';
      CKEDITOR.addCss('.cke_editable { font-size: 18px; }');
      const self = this;
      $('#datePollComment').ckeditor({
        customConfig: '/commons-extension/ckeditorCustom/config.js',
        extraPlugins: extraPlugins,
        removePlugins: 'image,confirmBeforeReload,maximize,suggester,resize',
        autoGrow_onStartup: true,
        on: {
          instanceReady: function() {
            self.editorReady = true;
          },
          change: function(evt) {
            self.message = evt.editor.getData();
          },
        },
      });
    },
    show() {
      this.visible = !this.visible;
    },
    saveComment() {
      this.saving = true;
      if (this.activityId) {
        this.$eventCommentService.createEventComment(this.activityId, this.message)
          .then(() => {
            this.visible = false;
            this.initCKEditor();
            this.$emit('saved');
          })
          .finally(() => this.saving = false);
      } else {
        this.$eventCommentService.createEventActivity(this.event.id, this.event.calendar.owner.id)
          .then((activity) => {
            this.event.activityId = activity.id;
            return this.$eventService.updateEventFields(this.event.id, {
              activityId: this.event.activityId,
            });
          })
          .then(() => this.$eventService.updateEventFields(this.event.activityId, this.message))
          .then(() => this.$eventCommentService.createEventComment(this.event.activityId, this.message))
          .then(() => {
            this.visible = false;
            this.initCKEditor();
            this.$emit('saved');
          })
          .finally(() => this.saving = false);
      }
    },
  }
};
</script>