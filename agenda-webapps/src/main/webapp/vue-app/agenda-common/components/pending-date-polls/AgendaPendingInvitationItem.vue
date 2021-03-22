<template>
  <v-list-item
    :style="eventStyle"
    dense
    class="pending-invitation-item border-radius"
    @click="openEventDetails">
    <v-list-item-icon class="my-auto mr-2">
      <v-icon
        v-if="isCreator"
        :style="textStyle">
        fa-crown
      </v-icon>
    </v-list-item-icon>
    <v-list-item-content class="pa-0">
      <v-list-item-title
        :style="textStyle"
        :title="invitedEvent.summary"
        class="white--text text-truncate subtitle-1">
        {{ invitedEvent.summary }}
      </v-list-item-title>
      <v-list-item-subtitle class="text-truncate d-flex caption mt-1">
        <span v-if="isDatePoll" :style="textStyle">
          {{ $t('agenda.ongoingDatePoll') }} - {{ displayVoteStatus }}
        </span>
        <template v-else>
          <span>
            {{ $t('agenda.pendingReply') }}
          </span>
          <date-format
            :value="invitedEvent.start"
            :format="dayFormat"
            class="ml-auto" />
        </template>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action class="my-1 py-0">
      <span
        v-if="isDatePoll"
        :title="displayVoteStatus"
        :class="voteIconStatus ">
      </span>
      <i
        :style="textStyle"
        :class="eventIcon"
        class="uiIcon32x32 uiIconPLFFont align-self-center d-flex mb-1 mr-1"></i>
    </v-list-item-action>
  </v-list-item>
</template>
<script>
export default {
  props: {
    invitedEvent: {
      type: Object,
      default: () => ({})
    },
  },
  data() {
    return {
      dialog: false,
      currentUserId: eXo.env.portal.userIdentityId,
      dayFormat: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      },
    };
  },
  computed: {
    isCreator() {
      return this.invitedEvent && this.invitedEvent.creator && Number(this.invitedEvent.creator.id) === Number(this.currentUserId);
    },
    isDatePoll() {
      return this.invitedEvent && this.invitedEvent.status === 'TENTATIVE';
    },
    currentAttendee() {
      return this.invitedEvent.attendees && this.invitedEvent.attendees.find(attendee => attendee.identity.id === this.currentUserId);
    },
    hasVoted() {
      return this.currentAttendee && this.currentAttendee.response === 'TENTATIVE';
    },
    textClass() {
      return this.invitedEvent ? 'white-text':'primary--text';
    },
    voteIconStatus() {
      return this.currentAttendee && this.currentAttendee.response && `attendee-vote-${this.currentAttendee.response.toLowerCase()}` || 'attendee-vote-needs_action';
    },
    displayVoteStatus() {
      if (this.hasVoted) {
        return this.$t('agenda.datePollVoted');
      }
      return this.$t('agenda.datePollVoteNeeded');
    },
    eventIcon() {
      return this.isDatePoll && 'uiIconStatistics' || 'uiIconCalendarEmpty';
    },
    eventColor() {
      const eventColor = this.invitedEvent && (this.invitedEvent.color || this.invitedEvent.calendar && this.invitedEvent.calendar.color) || '#2196F3';
      if (this.invitedEvent && this.$agendaUtils.toDate(this.invitedEvent.end).getTime() > Date.now()) {
        return eventColor;
      } else {
        return this.$agendaUtils.addOpacity(eventColor, 40);
      }
    },
    textStyle() {
      if (this.isDatePoll) {
        return 'color: #fff !important';
      } else {
        return `color: ${this.eventColor} !important`;
      }
    },
    eventStyle() {
      if (this.isDatePoll) {
        return `background: ${this.eventColor};`;
      }
      return `border: 1px solid ${this.eventColor};`;
    }
  },
  methods: {
    openEventDetails() {
      this.$root.$emit('agenda-event-details', this.invitedEvent);
      this.$emit('close');
    },
  }
};
</script>
