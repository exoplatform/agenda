<template>
  <div v-if="!isPastEvent" class="d-flex flex-column">
    <div class="title d-md-none mt-2 mb-3 mx-4">
      {{ $t('agenda.doYouParticipate') }}
    </div>
    <div class="event-attendees-responses d-flex ms-auto">
      <v-btn
        :loading="savingResponse === seatResponse"
        :disabled="savingResponse"
        :class="eventResponse === seatResponse && 'btn-primary'"
        :value="seatResponse"
        class="btn border-radius"
        @click="changeResponse(seatResponse)">
        <v-icon size="20" class="me-4">{{ waits ? 'fas fa-info-circle' : 'fas fa-check-circle' }}</v-icon>
        {{ waits ? $t('agenda.button.waitingList') : $t('agenda.button.yes') }}
      </v-btn>
      <v-btn
        :loading="savingResponse === 'DECLINED'"
        :disabled="savingResponse"
        :class="eventResponse === 'DECLINED' && 'btn-primary'"
        value="DECLINED"
        class="btn border-radius mx-2"
        @click="changeResponse('DECLINED')">
        <v-icon size="20" class="me-4">fas fa-times-circle</v-icon>
        {{ $t('agenda.button.no') }}
      </v-btn>
      <v-btn
        :loading="savingResponse === 'TENTATIVE'"
        :disabled="savingResponse"
        :class="eventResponse === 'TENTATIVE' && 'btn-primary'"
        value="TENTATIVE"
        class="btn border-radius"
        @click="changeResponse('TENTATIVE')">
        <v-icon size="20" class="me-4">fas fa-question-circle</v-icon>
        {{ $t('agenda.button.maybe') }}
      </v-btn>
      <agenda-recurrent-event-response-confirm-dialog
        v-if="event.occurrence"
        ref="responseConfirmDialog"
        :event="event" />
    </div>
    <span v-if="capacityLine" class="caption text-sub-title ms-auto mt-1">
      {{ capacityLine }}
    </span>
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
    isPastEvent() {
      if (!this.event) {
        return true;
      } else if (this.event.start && this.event.end) {
        const endDate = this.$agendaUtils.toDate(this.event.end);
        return endDate.getTime() < Date.now();
      } else {
        return false;
      }
    },
    userAttendee() {
      if (this.event && this.event.attendees && this.event.attendees.length) {
        return this.event.attendees.find(attendee => attendee.identity && Number(attendee.identity.id) === Number(eXo.env.portal.userIdentityId));
      }
      return null;
    },
    eventResponse() {
      return this.userAttendee && this.userAttendee.response || 'NEEDS_ACTION';
    },
    /**
     * @returns {Boolean} whether the seat answer is the waiting list: the
     *          event is full for someone who holds no seat there, or they wait
     *          already. The counts are the displayed date's.
     */
    waits() {
      return this.eventResponse === 'WAITING'
        || this.event.capacity > 0 && this.event.acceptedCount >= this.event.capacity && this.eventResponse !== 'ACCEPTED';
    },
    seatResponse() {
      return this.waits ? 'WAITING' : 'ACCEPTED';
    },
    /**
     * @returns {String} the place of a waiting viewer in the line, otherwise
     *          the limit of an event that has one
     */
    capacityLine() {
      if (this.eventResponse === 'WAITING' && this.event.waitingListPosition) {
        return this.$t('agenda.capacity.waitingPosition', {0: this.event.waitingListPosition});
      }
      return this.event.capacity > 0 && this.$t('agenda.capacity.indicator', {0: this.event.capacity}) || '';
    },
  },
  methods: {
    changeResponse(response) {
      // A self-registrant on an open series picks the same scope an invitee
      // does (this occurrence, upcoming, all); parent.open is the series flag
      const parent = this.event.parent;
      if (this.event.occurrence && parent && (!parent.acl || parent.acl.attendee || parent.open)) {
        this.$refs.responseConfirmDialog.open(response);
      } else {
        this.savingResponse = response;
        this.$eventService.sendEventResponse(this.event.id, null, response)
          .then(outcome => {
            // The server decides the seat: what follows reads its answer
            this.$root.$emit('agenda-event-response-sent', this.event, null, outcome && outcome.response || response);
            if (this.event.recurrence) {
              this.$agendaUtils.announcePartlyWaiting(this, this.event.id, outcome && outcome.firstWaitingOccurrenceId);
            }
          })
          .finally(() => this.savingResponse = false);
      }
    },
  },
};
</script>