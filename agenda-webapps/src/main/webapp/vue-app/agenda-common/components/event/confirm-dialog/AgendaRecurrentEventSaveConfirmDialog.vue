<template>
  <v-dialog
    ref="dialog"
    v-model="dialog"
    content-class="uiPopup"
    width="300">
    <v-card class="elevation-12">
      <div class="ignore-vuetify-classes popupHeader ClearFix">
        <a
          class="uiIconClose pull-right"
          aria-hidden="true"
          @click="close"></a>
        <span class="ignore-vuetify-classes PopupTitle popupTitle text-title text-truncate">
          {{ $t('agenda.confirmSaveRecurrentEventTitle') }}
        </span>
      </div>
      <v-card-text>
        <v-radio-group v-model="recurrenceModificationType">
          <v-radio :label="$t('agenda.onlyThisEvent')" value="single" />
          <v-radio :label="$t('agenda.thisAndUpcomingEvents')" value="upcoming" />
          <v-radio :label="$t('agenda.allEvents')" value="all" />
        </v-radio-group>
      </v-card-text>
      <v-card-actions class="d-flex flex-wrap justify-center center">
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn me-2 mb-1"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </button>
        <button
          :disabled="loading"
          :loading="loading"
          class="ignore-vuetify-classes btn-primary ms-2 mb-1"
          @click="saveRecurrentEventChoice">
          {{ $t('agenda.button.save') }}
        </button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
// One step above the platform stacking scale, whose planes are named in
// platform-ui: drawer 1035, modal backdrop 1040, modal 1050, snackbar 1060. A
// scope question is blocking, so it is painted over all of them.
//
// Why a fixed plane rather than the skin rule meant to do this: that rule reads
// `z-index: var(--allPagesZindexModal, 1050) !important` and, where the custom
// property is declared EMPTY, a var() does not fall back — the substitution is
// invalid at computed-value time and the whole declaration is dropped,
// !important included. Measured on this platform: the popup keeps z-index 3
// while its own veil is painted over it. Measuring the screen instead was tried
// and is worse: Vuetify creates that veil in a requestAnimationFrame, so there
// is nothing to measure when the popup opens.
const ABOVE_STACKING_SCALE = 1070;

export default {
  props: {
    /**
     * The fields the host is changing, when it is changing named fields rather
     * than saving the event the organiser has in front of them.
     *
     * Null — the default, and what the event form passes — keeps the original
     * contract: "this and upcoming" and "all events" promote the displayed
     * date's whole state, which is right for a form, where every one of those
     * fields is on screen and was just edited.
     *
     * A host that changes one thing from elsewhere — the participants drawer of
     * the event page changes the padlock and the invitee list — passes the list
     * instead. The series then keeps everything else: its own summary, its own
     * location, and above all its own time of day, which the displayed date may
     * have moved on its own (eXIP 7.3.0.20, US06).
     */
    changedFields: {
      type: Array,
      default: null,
    },
    /**
     * What a host did to the participant list, as a delta rather than a list.
     *
     * A wider scope acts on a list that is <em>not</em> the one the host
     * edited: a participant removed from one date alone is still on the series
     * — the product says so, and a test pins both halves — so replacing the
     * series' list with that date's would uninvite them from the whole meeting,
     * and from every other date with it, since a series change reaches them
     * all. The same shape the server already uses when it carries a membership
     * change into the dates individually modified.
     *
     * The identities are named by their participant key, not by their id: a
     * participant added through the suggester carries only
     * {providerId, remoteId, profile} and gains an id once the server resolves
     * them, so an id-keyed delta never recognises the very addition it is there
     * to carry.
     *
     * Null means the host changed no participant.
     */
    attendeesDelta: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    event: null,
    changeDatesOnly: false,
    recurrenceModificationType: 'single',
    loading: false,
    dialog: false,
  }),
  watch: {
    dialog() {
      if (this.dialog) {
        this.$emit('dialog-opened');
      } else {
        this.$emit('dialog-closed');
      }
    },
  },
  created() {
    $(document).on('keydown', (event) => {
      if (event.key === 'Escape') {
        this.dialog = false;
      }
    });
  },
  methods: {
    saveRecurrentEventChoice(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }
      if (this.recurrenceModificationType === 'single') {
        this.saveOccurrenceEvent();
      } else if (this.recurrenceModificationType === 'all') {
        this.saveRecurrentEvent();
      } else if (this.recurrenceModificationType === 'upcoming') {
        this.saveUpcomingEvents();
      }
    },
    saveUpcomingEvents(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      let recurrentEvent = this.event.parent;
      return this.$eventService.getEventById(recurrentEvent.id, 'all')
        .then(parentRecurrentEvent => {
          recurrentEvent = JSON.parse(JSON.stringify(parentRecurrentEvent));
          const untilDate = new Date(this.event.occurrence.id);
          untilDate.setDate(untilDate.getDate() - 1);
          untilDate.setHours(23);
          untilDate.setMinutes(59);
          untilDate.setSeconds(59);
          const startDate = new Date(recurrentEvent.start);

          // If the modified occurrence is the first occurrence of the recurring event
          // Then change all current recurrent event
          if (startDate >= untilDate) {
            return this.saveRecurrentEvent();
          } else {
            parentRecurrentEvent.recurrence.until = this.$agendaUtils.toRFC3339(untilDate);
            parentRecurrentEvent.sendInvitation = false;
            return this.$eventService.updateEvent(parentRecurrentEvent)
              .then(() => {
                if (this.changedFields) {
                  // The second series continues the first one and keeps its
                  // rule, its fields and its time of day; it only starts on the
                  // displayed date, and carries the fields the host changed
                  recurrentEvent.start = this.$agendaUtils.getSameTime(this.event.start, recurrentEvent.start);
                  recurrentEvent.end = this.$agendaUtils.getSameTime(this.event.end, recurrentEvent.end);
                  this.changedFields.forEach(field => {
                    recurrentEvent[field] = this.event[field];
                  });
                  this.applyAttendeesDelta(recurrentEvent);
                  recurrentEvent.sendInvitation = !!this.event.sendInvitation;
                } else {
                  recurrentEvent.start = this.event.start;
                  recurrentEvent.end = this.event.end;
                  recurrentEvent.attachments = this.event.attachments;
                  recurrentEvent.attendees = this.event.attendees;
                  recurrentEvent.conferences = this.event.conferences;
                  recurrentEvent.description = this.event.description;
                  recurrentEvent.location = this.event.location;
                  // The padlock is a value of the form like the others since
                  // US06: without this line the new series would be created
                  // with the flag the old one carried, and "this and upcoming
                  // events" would silently drop the change the organiser just
                  // made
                  recurrentEvent.open = this.event.open;
                  recurrentEvent.summary = this.event.summary;
                  recurrentEvent.dateOptions = this.event.dateOptions || [];
                  if (this.event.recurrence) {
                    recurrentEvent.recurrence = this.event.recurrence;
                  } else {
                    const eventRecurrence = this.event?.recurrence || this.event?.parent?.recurrence;
                    const recurrenceType = eventRecurrence?.type || 'NO_REPEAT';
                    if (recurrenceType === 'WEEKLY') {
                      const dayNameFromDate = this.$agendaUtils.getDayNameFromDate(this.event.start);
                      recurrentEvent.recurrence.byDay = [dayNameFromDate.substring(0, 2).toUpperCase()];
                    }

                  }
                }
                delete recurrentEvent.id;
                // Returned as a promise rather than left as a bare setTimeout:
                // the catch below guards the chain this callback returns, and a
                // timer that is merely started settles nothing, so the chain
                // completed before the creation was even attempted. That is the
                // failure that costs the most — the first series has already
                // been shortened above, so a creation that fails in silence
                // leaves the organiser with a truncated series and no
                // continuation
                return new Promise(resolve => setTimeout(resolve, 200))
                  .then(() => this.$eventService.createEvent(recurrentEvent))
                  .then(createdEvent => {
                    recurrentEvent = createdEvent;
                    this.close();
                    this.$root.$emit('agenda-event-saved', recurrentEvent);
                  });
              });           
          }
        })
        // Reaches all three calls of this scope — the read, the shortening and
        // the creation. The other two scopes are answered by their host's
        // catch; this one does its own work, so it carries its own
        .catch(this.saveFailed);
    },
    /**
     * This scope is the one that acts on its own instead of handing an event
     * back to its host, so a rejection has no other handler to reach.
     *
     * @param {Object} error the rejected response
     * @returns {void}
     */
    saveFailed(error) {
      this.close();
      this.$root.$emit('agenda-event-save-error', this.event, error);
      this.$root.$emit('alert-message', this.$t('agenda.eventSave.error'), 'error');
    },
    saveRecurrentEvent(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      if (this.changedFields) {
        // The host is changing named fields, not the event in front of the
        // organiser: the series keeps everything else, its own dates included.
        // Read back from the server rather than taken from this.event.parent,
        // so that what is sent is the whole stored series and not the reduced
        // object a page carries
        return this.$eventService.getEventById(this.event.parent.id, 'all')
          .then(storedSeries => {
            const seriesToSave = JSON.parse(JSON.stringify(storedSeries));
            this.changedFields.forEach(field => {
              seriesToSave[field] = this.event[field];
            });
            this.applyAttendeesDelta(seriesToSave);
            seriesToSave.sendInvitation = !!this.event.sendInvitation;
            this.$emit('save-event', seriesToSave, this.changeDatesOnly);
          })
          // The host's catch is on the save this read would have produced, so a
          // failure here reaches nothing without this
          .catch(this.saveFailed);
      }

      const eventToSave = JSON.parse(JSON.stringify(this.event));
      eventToSave.id = this.event.parent.id;
      eventToSave.remoteId = eventToSave.parent && eventToSave.parent.remoteId || '';
      eventToSave.remoteProviderId = eventToSave.parent && eventToSave.parent.remoteProviderId || 0;
      eventToSave.recurrence = this.event.recurrence || this.event.parent.recurrence;
      eventToSave.occurrence = null;
      eventToSave.parent = null;
      // Keep same original recurrent event dates, and change only time
      eventToSave.start = this.$agendaUtils.getSameTime(this.event.parent.start, this.event.start);
      eventToSave.end = this.$agendaUtils.getSameTime(this.event.parent.end, this.event.end);

      this.$emit('save-event', eventToSave, this.changeDatesOnly);
    },
    /**
     * Applies the host's participant change to one event's own list, adding
     * whoever was added and removing whoever was removed, and leaving everyone
     * else where they are.
     *
     * @param {Object} target the event a wider scope is about to save
     * @returns {void}
     */
    applyAttendeesDelta(target) {
      if (!this.attendeesDelta) {
        return;
      }
      const removedKeys = this.attendeesDelta.removedParticipantKeys || [];
      const kept = (target.attendees || [])
        .filter(attendee => !removedKeys.includes(this.$agendaUtils.participantKey(attendee)));
      const presentKeys = kept.map(attendee => this.$agendaUtils.participantKey(attendee));
      (this.attendeesDelta.added || []).forEach(attendee => {
        const key = this.$agendaUtils.participantKey(attendee);
        if (!presentKeys.includes(key)) {
          kept.push(attendee);
          presentKeys.push(key);
        }
      });
      target.attendees = kept;
    },
    saveOccurrenceEvent(eventObject) {
      if (eventObject) {
        eventObject.preventDefault();
        eventObject.stopPropagation();
      }

      const eventToSave = JSON.parse(JSON.stringify(this.event));
      this.$emit('save-event', eventToSave, this.changeDatesOnly);
    },
    close(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }

      const content = this.$refs.dialog && this.$refs.dialog.$refs.content;
      if (content) {
        // Given back to the skin: the next opening measures the drawers as they
        // are then, and a value left behind would outlive the reason for it
        content.style.removeProperty('z-index');
      }
      this.dialog = false;
    },
    open(event, changeDatesOnly) {
      this.event = event;
      this.recurrenceModificationType = 'single';
      this.changeDatesOnly = changeDatesOnly;
      this.dialog = true;
      this.$nextTick(this.raiseAboveOverlays);
    },
    /**
     * Paints the popup above the platform stacking scale.
     *
     * No CSS rule of ours can do it: content-class lands on the inner .v-dialog
     * while the z-index sits on .v-dialog__content, which carries no class we
     * control. An inline value with important is the one step above a skin
     * rule. The reasoning behind the constant is on its declaration.
     *
     * Its own veil is deliberately left where Vuetify puts it: it keeps dimming
     * the page, below the answer instead of over it.
     *
     * @returns {void}
     */
    raiseAboveOverlays() {
      const content = this.$refs.dialog && this.$refs.dialog.$refs.content;
      if (content) {
        content.style.setProperty('z-index', String(ABOVE_STACKING_SCALE), 'important');
      }
    },
  },
};
</script>
