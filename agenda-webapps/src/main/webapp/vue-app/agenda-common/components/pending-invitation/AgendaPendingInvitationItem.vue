<template>
  <v-list-item
    :style="datePollStyle"
    dense
    class="border-radius"
    @click="openDatePollDetails">
    <v-list-item-content class="pa-0">
      <v-list-item-title :title="datePoll.summary" class="white--text text-truncate subtitle-1">{{ datePoll.summary }}</v-list-item-title>
      <v-list-item-subtitle :title="displayVoteStatus" class="white--text text-truncate d-flex caption">{{ $t('agenda.ongoingDatePoll') }} - {{ displayVoteStatus }}</v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action class="my-1 py-0">
      <span :title="displayVoteStatus" :class="voteIconStatus "></span>
      <i v-if="isTentativeEvent" class="uiIconStatistics white--text uiIcon32x32 uiIconPLFFont mb-1 mr-1"></i>
    </v-list-item-action>
  </v-list-item>
</template>
<script>
export default {
  props: {
    datePoll: {
      type: Object,
      default: () => ({})
    },
  },
  data() {
    return {
      dialog: false,
      currentUserId: eXo.env.portal.userIdentityId,
    };
  },
  computed:{
    isTentativeEvent() {
      return this.datePoll && this.datePoll.status === 'TENTATIVE';
    },
    currentAttendee() {
      return this.datePoll.attendees.find(attendee => attendee.identity.id === this.currentUserId);
    },
    hasVoted() {
      return this.currentAttendee && this.currentAttendee.response === 'TENTATIVE';
    },
    textClass() {
      return this.datePoll ? 'white-text':'primary--text';
    },
    voteIconStatus() {
      return this.datePoll && this.datePoll.attendees && `attendee-vote-${this.currentAttendee.response.toLowerCase()}`;
    },
    displayVoteStatus() {
      if(this.hasVoted) {
        return this.$t('agenda.datePollVoted');
      }
      return this.$t('agenda.datePollVoteNeeded');
    },
    datePollStyle() {
      if(this.datePoll.color) {
        return `background: ${this.datePoll.color}`;
      } else {
        return `background: ${this.datePoll.calendar.color}`;
      }
    }
  },
  methods:{
    openDatePollDetails() {
      this.$root.$emit('agenda-event-details', this.datePoll);
      this.$emit('close');
    },
  }
};
</script>
