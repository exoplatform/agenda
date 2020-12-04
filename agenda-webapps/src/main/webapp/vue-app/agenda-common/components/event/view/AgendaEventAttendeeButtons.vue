<template>
  <div class="d-flex flex-column">
    <div class="title d-md-none mt-2 mb-3 mx-4">
      {{ $t('agenda.doYouParticipate') }}
    </div>
    <div class="event-attendees-responses d-flex ml-auto">
      <v-btn
        :loading="savingResponse === 'ACCEPTED'"
        :disabled="savingResponse"
        :class="eventResponse === 'ACCEPTED' && 'btn-primary'"
        value="ACCEPTED"
        class="btn border-radius"
        @click="changeResponse('ACCEPTED')">
        <v-icon size="20" class="mr-4">fas fa-check-circle</v-icon>
        {{ $t('agenda.button.yes') }}
      </v-btn>
      <v-btn
        :loading="savingResponse === 'DECLINED'"
        :disabled="savingResponse"
        :class="eventResponse === 'DECLINED' && 'btn-primary'"
        value="DECLINED"
        class="btn border-radius mx-2"
        @click="changeResponse('DECLINED')">
        <v-icon size="20" class="mr-4">fas fa-times-circle</v-icon>
        {{ $t('agenda.button.no') }}
      </v-btn>
      <v-btn
        :loading="savingResponse === 'TENTATIVE'"
        :disabled="savingResponse"
        :class="eventResponse === 'TENTATIVE' && 'btn-primary'"
        value="TENTATIVE"
        class="btn border-radius"
        @click="changeResponse('TENTATIVE')">
        <v-icon size="20" class="mr-4">fas fa-info-circle</v-icon>
        {{ $t('agenda.button.maybe') }}
      </v-btn>
      <agenda-recurrent-event-response-confirm-dialog
        v-if="event.occurrence"
        ref="responseConfirmDialog"
        :event="event" />
    </div>
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
    savingResponse: false,
  }),
  computed: {
    userAttendee() {
      if (this.event && this.event.attendees && this.event.attendees.length) {
        return this.event.attendees.find(attendee => attendee.identity && Number(attendee.identity.id) === Number(eXo.env.portal.userIdentityId));
      }
      return null;
    },
    eventResponse() {
      return this.userAttendee && this.userAttendee.response || 'NEEDS_ACTION';
    },
  },
  methods: {
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