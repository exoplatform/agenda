<template>
  <v-col>
    <v-row class="event-attendees-responses align-center d-flex mb-10">
      <i class="uiIconGroup darkGreyIcon uiIcon32x32 pr-5"></i>
      <span>{{ attendeesResponsesTitle }}</span>
    </v-row>
    <v-row class="event-attendees ml-10 flex-column">
      <agenda-event-attendee-item
        v-for="attendee in attendees"
        :key="attendee"
        :attendee="attendee" />
    </v-row>
  </v-col>
</template>

<script>
export default {
  props: {
    attendees: {
      type: Object,
      default: () => ({})
    },
  },
  computed: {
    acceptedResponsesCount() {
      if (!this.attendees || !this.attendees.length) {
        return 0;
      }
      return this.attendees.filter(attendee => attendee && attendee.response === 'ACCEPTED').length;
    },
    refusedResponsesCount() {
      if (!this.attendees || !this.attendees.length) {
        return 0;
      }
      return this.attendees.filter(attendee => attendee && attendee.response === 'REFUSED').length;
    },
    tentativeResponsesCount() {
      if (!this.attendees || !this.attendees.length) {
        return 0;
      }
      return this.attendees.filter(attendee => attendee && attendee.response === 'TENTATIVE').length;
    },
    needsActionResponsesCount() {
      if (!this.attendees || !this.attendees.length) {
        return 0;
      }
      return this.attendees.filter(attendee => attendee && attendee.response === 'NEEDS_ACTION').length;
    },
    attendeesResponsesTitle() {
      return this.$t('agenda.attendeesResponsesOverview', {
        0: this.acceptedResponsesCount,
        1: this.refusedResponsesCount,
        2: this.needsActionResponsesCount,
        3: this.tentativeResponsesCount,
      });
    },
  }
};
</script>