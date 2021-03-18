<template>
  <div v-if="comments" class="contentBox">
    <div class="commentBox">
      <div class="commentList">
        <v-expand-transition
          v-for="comment in comments"
          :key="comment.id">
          <agenda-event-date-option-comment
            :comment="comment"
            @deleteComment="deleteCommentConfirm(comment)" />
        </v-expand-transition>
      </div>
    </div>
    <exo-confirm-dialog
      ref="deleteCommentConfirm"
      :title="$t('agenda.title.deleteComment')"
      :message="$t('agenda.message.deleteComment')"
      :ok-label="$t('agenda.button.delete')"
      :cancel-label="$t('agenda.button.cancel')"
      persistent
      @ok="deleteComment()" />
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null
    },
  },
  data() {
    return {
      comments: null,
      commentToDelete: null,
      loading: false,
    };
  },
  created() {
    this.loadComments();
  },
  methods: {
    loadComments() {
      if (this.event && this.event.activityId) {
        this.loading = true;
        return this.$eventCommentService.getEventComments(this.event.activityId)
          .then(data => this.comments = data && data.comments)
          .finally(() => this.loading = false);
      }
    },
    deleteComment() {
      this.loading = true;
      return this.$eventCommentService.deleteEventComment(this.event.activityId, this.commentToDelete.id)
        .then(() => this.loadComments())
        .finally(() => this.loading = false);
    },
    deleteCommentConfirm(comment) {
      this.commentToDelete = comment;
      this.$refs.deleteCommentConfirm.open();
    },
  }
};
</script>
