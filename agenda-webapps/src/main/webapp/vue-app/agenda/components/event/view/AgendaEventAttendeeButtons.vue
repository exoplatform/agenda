<template>
  <div class="event-attendees-responses d-flex">
    <v-btn
      :loading="savingResponse === 'ACCEPTED'"
      :disabled="savingResponse"
      :class="eventResponse === 'ACCEPTED' && 'btn-primary'"
      value="ACCEPTED"
      class="btn border-radius"
      @click="changeResponse('ACCEPTED')">
      {{ $t('agenda.button.yes') }}
    </v-btn>
    <v-btn
      :loading="savingResponse === 'DECLINED'"
      :disabled="savingResponse"
      :class="eventResponse === 'DECLINED' && 'btn-primary'"
      value="DECLINED"
      class="btn border-radius mx-2"
      @click="changeResponse('DECLINED')">
      {{ $t('agenda.button.no') }}
    </v-btn>
    <v-btn
      :loading="savingResponse === 'TENTATIVE'"
      :disabled="savingResponse"
      :class="eventResponse === 'TENTATIVE' && 'btn-primary'"
      value="TENTATIVE"
      class="btn border-radius"
      @click="changeResponse('TENTATIVE')">
      {{ $t('agenda.button.maybe') }}
    </v-btn>
    <agenda-recurrent-event-response-confirm-dialog
      v-if="event.occurrence"
      ref="responseConfirmDialog"
      :event="event"
      @dialog-closed="resetEventResponse" />
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
  data: () => ({
    eventResponse: 'NEEDS_ACTION',
    savingResponse: false,
  }),
  computed: {
    attendees() {
      return this.event && this.event.attendees;
    },
    userAttendee() {
      if (this.attendees && this.attendees.length) {
        return this.attendees.find(attendee => attendee.identity && Number(attendee.identity.id) === Number(eXo.env.portal.userIdentityId));
      }
      return null;
    },
  },
  methods: {
    reset() {
      this.resetEventResponse();
    },
    resetEventResponse() {
      this.eventResponse = this.userAttendee && this.userAttendee.response || 'NEEDS_ACTION';
      this.savingResponse = false;
    },
    changeResponse(response) {
      if (this.event.occurrence) {
        this.$refs.responseConfirmDialog.open(response);
      } else {
        this.savingResponse = response;
        this.$eventService.sendEventResponse(this.event.id, null, response)
          .then(event => this.$root.$emit('agenda-event-response-sent', event, null, response))
          .finally(() => this.savingResponse = false);
      }
    },
  },
};
</script>