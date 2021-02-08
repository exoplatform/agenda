<template>
  <v-card
    class="agendaSearchCard d-flex flex-column border-radius box-shadow"
    flat
    min-height="227">
    <v-card-text v-if="ownerProfile" class="px-2 pt-2 pb-0">
      <a class="flex-nowrap flex-shrink-0 d-flex spaceAvatar">
        <v-avatar
          :size="spaceAvatarSize"
          tile
          class="pull-left my-auto">
          <v-img
            :src="eventOwnerAvatarUrl"
            :height="spaceAvatarSize"
            :width="spaceAvatarSize"
            :max-height="spaceAvatarSize"
            :max-width="spaceAvatarSize"
            class="mx-auto" />
        </v-avatar>
        <div v-if="eventTitle" class="d-flex flex-column text-truncate pull-left ml-2">
          <a
            v-if="eventTitle"
            v-sanitized-html="eventTitle"
            :href="eventUrl"
            :title="eventTitleText"
            class="pt-2 text-left text-truncate">
          </a>
          <a
            v-if="eventOwnerDisplayName"
            :href="calendarOwnerLink"
            class="text-sub-title my-0">
            <slot name="subTitle">
              {{ eventOwnerDisplayName }}
            </slot>
          </a>
        </div>
      </a>
    </v-card-text>
    <div class="mx-auto d-flex flex-grow-1 px-3 py-0">
      <div
        ref="excerptNode"
        :title="excerptText"
        class="text-wrap text-break caption flex-grow-1">
      </div>
    </div>
    <v-list class="light-grey-background flex-grow-0 border-top-color no-border-radius pa-0">
      <v-list-item
        v-if="isConfirmedEvent"
        class="px-0 pt-1 pb-2"
        two-line>
        <v-list-item-icon class="mx-0 my-auto">
          <span class="uiIconPLFEventTask tertiary--text pl-1 pr-2 display-1"></span>
        </v-list-item-icon>
        <v-list-item-content>
          <v-list-item-title>
            <date-format
              :value="eventStartDate"
              :format="fullDateFormat"
              class="mr-1" />
            <date-format
              :value="eventStartDate"
              :format="dateTimeFormat"
              class="mr-1" />
          </v-list-item-title>
        </v-list-item-content>
      </v-list-item>
      <v-list-item
        v-else-if="isTentativeEvent"
        class="px-2 pt-1 pb-2"
        two-line>
        <v-list-item-icon class="mx-0 my-auto">
          <span class="uiIconStatistics uiIcon32x32 uiIconPLFFont tertiary--text pl-1 pr-2"></span>
        </v-list-item-icon>
        <v-list-item-content class="pb-0">
          <v-list-item-title>
            {{ voteLabel }}
          </v-list-item-title>
        </v-list-item-content>
      </v-list-item>
    </v-list>
  </v-card>
</template>

<script>
export default {
  props: {
    result: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    lineHeight: 22,
    spaceAvatarSize: 37,
    maxEllipsisHeight: 92,
    currentUserId: eXo.env.portal.userIdentityId,
    fullDateFormat: {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    },
    dateTimeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    }
  }),
  computed: {
    labels() {
      return {
        CancelRequest: this.$t('profile.CancelRequest'),
        Confirm: this.$t('profile.Confirm'),
        Connect: this.$t('profile.Connect'),
        Ignore: this.$t('profile.Ignore'),
        RemoveConnection: this.$t('profile.RemoveConnection'),
        StatusTitle: this.$t('profile.StatusTitle'),
        join: this.$t('space.join'),
        leave: this.$t('space.leave'),
        members: this.$t('space.members'),
      };
    },
    eventUrl() {
      return this.result && this.result.id && `${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda?eventId=${this.result.id}`;
    },
    eventDescription() {
      return this.result && this.result.description;
    },
    excerpts() {
      return this.result && this.result.excerpts;
    },
    excerptHtml() {
      return this.excerpts && this.excerpts.concat('\r\n...');
    },
    excerptText() {
      return this.excerpts.length ? $('<div />').html(this.excerptHtml).text() : this.eventDescription;
    },
    eventStartDate() {
      return this.result && this.result.start;
    },
    eventTitle() {
      return this.result && this.result.summary || '';
    },
    eventTitleText() {
      return $('<div />').html(this.eventTitle).text();
    },
    owner() {
      return this.result && this.result.calendar && this.result.calendar.owner;
    },
    ownerProfile() {
      return this.owner && (this.owner.profile || this.owner.space);
    },
    calendarOwnerLink() {
      if (this.owner) {
        if (this.owner.providerId === 'organization') {
          return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.owner.remoteId}`;
        } else if (this.owner.providerId === 'space') {
          return `${eXo.env.portal.context}/g/:spaces:${this.owner.remoteId}/`;
        }
      }
      return '';
    },
    eventOwnerDisplayName() {
      return this.ownerProfile && this.ownerProfile.displayName;
    },
    eventOwnerAvatarUrl() {
      return this.ownerProfile && this.ownerProfile.avatarUrl;
    },
    isConfirmedEvent() {
      return this.result && (this.result.status === 'CONFIRMED' || !this.result.status);
    },
    isTentativeEvent() {
      return this.result && this.result.status === 'TENTATIVE';
    },
    currentAttendee() {
      return this.result.attendees.find(attendee => attendee.identity.id === this.currentUserId);
    },
    hasVoted() {
      return this.currentAttendee && this.currentAttendee.response === 'TENTATIVE';
    },
    voteLabel() {
      if (!this.currentAttendee) {
        return this.$t('agenda.datePoll');
      }
      return this.hasVoted && this.$t('agenda.datePollVoted') || this.$t('agenda.datePollVoteNeeded');
    },
  },
  mounted() {
    this.computeEllipsis();
  },
  methods: {
    computeEllipsis() {
      if (!this.excerptHtml || this.excerptHtml.length === 0) {
        return;
      }
      const excerptParent = this.$refs.excerptNode;
      if (!excerptParent) {
        return;
      }
      excerptParent.innerHTML = this.excerpts.length ? this.excerptHtml : this.eventDescription;
      if (excerptParent.innerHTML.length) {
        const charsToDelete = 50;
        let excerptParentHeight = excerptParent.getBoundingClientRect().height || this.lineHeight;
        let lastExcerptParentHeight = 0;
        if (excerptParentHeight > this.maxEllipsisHeight) {
          let diffWithPrevious = excerptParentHeight;
          let deleteCharsTentatives = 3;
          while (excerptParentHeight > this.maxEllipsisHeight && (diffWithPrevious > 0 || deleteCharsTentatives)) {
            excerptParent.innerHTML = this.deleteLastChars(excerptParent.innerHTML.replace(/&[a-z]*;/, ''), charsToDelete);
            lastExcerptParentHeight = excerptParentHeight;
            excerptParentHeight = excerptParent.getBoundingClientRect().height || this.lineHeight;
            diffWithPrevious = lastExcerptParentHeight - excerptParentHeight;
            if (diffWithPrevious === 0 && excerptParentHeight > this.maxEllipsisHeight) {
              deleteCharsTentatives--;
            }
          }
          excerptParent.innerHTML = this.deleteLastChars(excerptParent.innerHTML, 4);
          excerptParent.innerHTML = `${excerptParent.innerHTML}...`;
        }
      }
    },
    deleteLastChars(html, charsToDelete) {
      if (html.slice(-1) === '>') {
        // Replace empty tags
        html = html.replace(/<[a-zA-Z 0-9 "'=]*><\/[a-zA-Z 0-9]*>$/g, '');
      }
      html = html.replace(/<br>(\.*)$/g, '');

      charsToDelete = charsToDelete || 1;

      let newHtml = '';
      if (html.slice(-1) === '>') {
        // Delete last inner html char
        html = html.replace(/(<br>)*$/g, '');
        newHtml = html.replace(new RegExp(`([^>]{${charsToDelete}})(</)([a-zA-Z 0-9]*)(>)$`), '$2$3');
        newHtml = $('<div />').html(newHtml).html().replace(/&[a-z]*;/, '');
        if (newHtml.length === html.length) {
          newHtml = html.replace(new RegExp('([^>]*)(</)([a-zA-Z 0-9]*)(>)$'), '$2$3');
        }
      } else {
        newHtml = html.substring(0, html.trimRight().length - charsToDelete);
      }
      return newHtml;
    }
  }
};
</script>
