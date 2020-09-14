<template>
  <v-col>
    <v-row class="event-attendees-responses align-center d-flex mb-10">
      <v-btn-toggle v-model="eventResponse">
        <v-btn
          :loading="savingResponse"
          :disabled="savingResponse"
          :class="eventResponse === 'ACCEPTED' && 'btn-primary'"
          value="ACCEPTED"
          class="btn border-radius"
          @click="changeResponse('ACCEPTED')">
          {{ $t('agenda.button.yes') }}
        </v-btn>
        <v-btn
          :loading="savingResponse"
          :disabled="savingResponse"
          :class="eventResponse === 'DECLINED' && 'btn-primary'"
          value="DECLINED"
          class="btn border-radius mx-4"
          @click="changeResponse('DECLINED')">
          {{ $t('agenda.button.no') }}
        </v-btn>
        <v-btn
          :loading="savingResponse"
          :disabled="savingResponse"
          :class="eventResponse === 'TENTATIVE' && 'btn-primary'"
          value="TENTATIVE"
          class="btn border-radius"
          @click="changeResponse('TENTATIVE')">
          {{ $t('agenda.button.maybe') }}
        </v-btn>
      </v-btn-toggle>
    </v-row>
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
    <agenda-recurrent-event-response-confirm-dialog
      v-if="event.occurrence"
      ref="responseConfirmDialog"
      :event="event"
      @dialog-closed="resetEventResponse" />
  </v-col>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null
    },
  },
  data: () => ({
    eventResponse: 'NEEDS_ACTION',
    savingResponse: false,
  }),
  computed: {
    attendees() {
      return this.event && this.event.attendees;
    },
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
      return this.attendees.filter(attendee => attendee && attendee.response === 'DECLINED').length;
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
  },
  methods: {
    changeResponse(response) {
      this.savingResponse = true;
      if (this.event.occurrence) {
        this.$refs.responseConfirmDialog.open(response);
      } else {
        this.$eventService.sendEventResponse(this.event.id, null, response)
          .then(event => this.$root.$emit('agenda-event-response-sent', event, null, response))
          .finally(() => this.savingResponse = false);
      }
    },
    reset() {
      this.resetEventResponse();
    },
    resetEventResponse() {
      this.eventResponse = 'NEEDS_ACTION';
      if (this.event && this.event.attendees && this.event.attendees.length) {
        const userAttendee = this.event.attendees.find(attendee => attendee.identity && Number(attendee.identity.id) === Number(eXo.env.portal.userIdentityId));
        if (userAttendee) {
          this.eventResponse = userAttendee.response;
        }
      }
      this.savingResponse = false;
    },
  },
};
</script>