<template>
  <div class="CommentBlock">
    <div class="commentItem">
      <div class="commmentLeft">
        <exo-user-avatar
          :username="authorUsername"
          :title="authorFullname"
          :size="30"
          :url="authorUrl" />
      </div>
      <!--end commentLeft-->
      <div class="commentRight">
        <div class="authorCommentContainer clearfix">
          <div class="author">
            <a class="primary-color--text font-weight-bold subtitle-2 pr-2">
              {{ authorFullname }}
              <span v-if="authorExternal" class="externalTagClass">
                {{ ` (${$t('label.external')})` }}
              </span>
            </a>
          </div>
          <div
            v-sanitized-html="comment.body"
            class="contentComment"></div>
          <div v-if="currentUserComment" class="removeCommentBtn">
            <v-btn
              :title="$t('agenda.button.delete')"
              :size="32"
              class="deleteCommentButton"
              icon
              small
              @click="$emit('deleteComment')">
              <i class="uiIconTrashMini uiIconLightGray "></i>
            </v-btn>
          </div>
        </div>
        <div class="clearfix"></div>
        <div class="actionCommentBar pl-0">
          <ul class="pull-left statusAction pl-0">
            <li class="dateTime pl-0">
              <a :title="commentFullDate">
                {{ relativeTime }}
              </a>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
const msPerMinute = 60 * 1000;
const msPerHour = msPerMinute * 60;
const msPerDay = msPerHour * 24;
const msPerMaxDays = msPerDay * 2;
const msPerMonth = msPerDay * 30;
const msPerMaxMonth = msPerMonth * 2;

export default {
  props: {
    comment: {
      type: Object,
      default: () => null
    },
  },
  data() {
    return {
      currentUserName: eXo.env.portal.userName,
      lang: eXo.env.portal.language,
      dateTimeFormat: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      },
    };
  },
  computed: {
    commentDate() {
      return this.comment && this.comment && new Date(this.comment.createDate);
    },
    relativeTime() {
      return this.getRelativeTime(this.commentDate);
    },
    commentFullDate() {
      return this.commentDate && this.$dateUtil.formatDateObjectToDisplay(this.commentDate, this.dateTimeFormat, this.lang) || '';
    },
    author() {
      return this.comment && this.comment.identity;
    },
    authorUsername() {
      return this.author && this.author.remoteId;
    },
    authorFullname() {
      return this.author && this.author.profile && this.author.profile.fullname;
    },
    authorUrl() {
      return this.authorUsername && `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.authorUsername}`;
    },
    authorExternal() {
      return this.author && this.author.profile && this.author.profile.external;
    },
    currentUserComment() {
      return eXo.env.portal.userName === this.authorUsername;
    },
  },
  methods: {
    removeComment() {
      this.$datePollCommentSevice.removeComment(this.comment.comment.id);
      for (let i = 0; i < this.comments.length; i++) {
        if (this.comments[i] === this.comment) {
          this.comments.splice(i, 1);
        }
      }
      this.$emit('confirmDialogClosed');
    },
    getRelativeTime(commentDate) {
      const elapsed = new Date().getTime() - new Date(commentDate).getTime();

      if (elapsed < msPerMinute) {
        return this.$t('TimeConvert.label.Less_Than_A_Minute');
      } else if (elapsed === msPerMinute) {
        return this.$t('TimeConvert.label.About_A_Minute');
      } else if (elapsed < msPerHour) {
        return this.$t('TimeConvert.label.About_?_Minutes', {0: Math.round(elapsed / msPerMinute)});
      } else if (elapsed === msPerHour) {
        return this.$t('TimeConvert.label.About_An_Hour');
      } else if (elapsed < msPerDay) {
        return this.$t('TimeConvert.label.About_?_Hours', {0: Math.round(elapsed / msPerHour)});
      } else if (elapsed === msPerDay) {
        return this.$t('TimeConvert.label.About_A_Day');
      } else if (elapsed < msPerMaxDays) {
        return this.$t('TimeConvert.label.About_?_Days', {0: Math.round(elapsed / msPerDay)});
      } else if (elapsed < msPerMonth) {
        return this.$t('TimeConvert.label.About_A_Month', {0: Math.round(elapsed / msPerDay)});
      } else if (elapsed < msPerMaxMonth) {
        return this.$t('TimeConvert.label.About_?_Months', {0: Math.round(elapsed / msPerDay)});
      } else {
        return this.displayCommentDate(this.comment.comment.createdTime.time);
      }
    },
    confirmCommentDelete: function () {
      this.$refs.CancelSavingCommentDialog.open();
    },
  }
};
</script>
