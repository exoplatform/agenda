<template>
  <v-card flat class="d-flex flex-column">
    <v-stepper
      v-if="event"
      v-model="stepper"
      class="d-flex flex-grow-1 flex-column">
      <v-stepper-header class="flex-grow-0 no-box-shadow border-bottom-color border-box-sizing">
        <div class="d-flex flex-grow-1">
          <v-stepper-step :complete="stepper > 1" step="1">
            {{ $t('agenda.stepEventDetails') }}
          </v-stepper-step>
          <v-divider class="eventFormStepperDivider" />
          <v-stepper-step step="2">
            {{ $t('agenda.stepEventChooseDate') }}
          </v-stepper-step>
        </div>
        <v-btn
          class="my-auto me-2"
          color="grey"
          icon
          dark
          @click="close">
          <v-icon>
            mdi-close
          </v-icon>
        </v-btn>
      </v-stepper-header>
      <v-stepper-items class="flex-grow-1">
        <v-stepper-content step="1">
          <agenda-event-form-basic-information
            ref="eventBasicInformation"
            :event="event"
            :display-time-in-form="displayTimes"
            :settings="settings"
            :connectors="connectors"
            :conference-provider="conferenceProvider"
            :selected-calendar="selectedCalendar"
            :current-space="currentSpace"
            @next-step="nextStep"
            @initialized="$emit('initialized')" />
        </v-stepper-content>
        <v-stepper-content step="2" class="pb-0">
          <agenda-event-form-dates
            v-if="stepper === 2"
            ref="eventDates"
            :settings="settings"
            :event="event"
            :connectors="connectors"
            :weekdays="weekdays"
            :working-time="workingTime"
            @date-option-added="updateDateOptionsLength"
            @date-option-deleted="updateDateOptionsLength" />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
    <v-divider />
    <div class="d-flex flex-grow-0 my-2">
      <v-btn
        v-if="stepper > 1"
        class="btn mx-2"
        @click="previousStep">
        <v-icon>{{ $vuetify.rtl && 'mdi-chevron-right' || 'mdi-chevron-left' }}</v-icon>
        <span class="d-none d-md-inline">
          {{ $t('agenda.button.previous') }}
        </span>
      </v-btn>
      <!-- ONE slot in the footer, carrying whichever line the step it is on
           owes the organiser. Two lines, never both at once — one belongs to
           the details step and one to the date step — so they share a place
           and a treatment rather than becoming two mechanisms that have to be
           kept looking alike.

           On step 1 it is the sentence the quick-add drawer deliberately does
           not carry: the drawer is minimal and gets the name only, while
           "Suggest several dates" sits here greyed and secondary next to the
           primary save and would otherwise tell a first-time organiser
           nothing about what pressing it produces.

           On step 2 it is the instruction that used to sit above the grid,
           under the busy-coverage report and the failed-source warning. Three
           stacked informational rows competed with each other and pushed the
           grid — the thing the organiser came to use — further down the
           screen. Here it costs the grid no height at all and sits beside the
           controls about to be pressed. The cost, plainly: it is further from
           the grid it refers to. "Drag on the calendar" names its own target,
           so proximity is doing little work here, but it is a real cost and
           not nothing.

           Below sm the footer's buttons already fill the row, so the line
           steps aside rather than wrapping them; text-truncate with the whole
           line on title keeps a narrow window clipping instead of pushing the
           buttons off the edge. -->
      <div
        v-if="footerHint"
        :title="$t(footerHint)"
        class="d-none d-sm-flex align-center ms-4 me-2 caption text-light-color text-truncate">
        {{ $t(footerHint) }}
      </div>
      <div class="ms-auto me-10">
        <v-btn
          v-if="displaySaveButton"
          :disabled="disableSaveButton"
          class="btn btn-primary me-2"
          @click="saveEvent">
          {{ saveButtonLabel }}
        </v-btn>
        <v-btn
          v-if="stepper < 2"
          :disabled="disableNextStepButton"
          :class="nextStepClass"
          @click="nextStep">
          {{ stepButtonLabel }}
        </v-btn>
        <v-btn
          class="btn ms-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
      </div>
    </div>
    <agenda-notification-alerts name="event-form" />
  </v-card>
</template>
<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
    event: {
      type: Object,
      default: () => ({}),
    },
    displayTimeInForm: {
      type: Boolean,
      default: false,
    },
    weekdays: {
      type: Array,
      default: () => null
    },
    workingTime: {
      type: Object,
      default: () => null
    },
    currentSpace: {
      type: Object,
      default: () => null
    },
    connectors: {
      type: Array,
      default: () => null
    },
    conferenceProvider: {
      type: Object,
      default: () => null
    },
    /*
     * Open straight on the date step instead of the details step. Set by the
     * quick-add drawer's "Suggest several dates" link, which has already
     * collected everything the details step asks for — the drawer gates that
     * link on isEventDetailsComplete, the very rule disableNextStepButton
     * applies, so this skips a step the organiser could only have walked
     * through unchanged.
     */
    openDateOptions: {
      type: Boolean,
      default: false
    },
  },
  data () {
    return {
      eventDateOptionsLength: 0,
      selectedCalendar: null,
      stepper: 0,
    };
  },
  computed: {
    eventCalendar() {
      return this.event && this.event.calendar;
    },
    eventOwner() {
      return this.eventCalendar && this.eventCalendar.owner;
    },
    /*
     * The details step's rule, held in AgendaUtils so the quick-add drawer's
     * date-poll link can apply the same one instead of restating it.
     */
    eventDetailsComplete() {
      return this.$agendaUtils.isEventDetailsComplete(this.event, this.$utils.htmlToText);
    },
    eventDateOptions() {
      return this.event && this.event.dateOptions || [];
    },
    displayTimes() {
      return this.displayTimeInForm && this.eventDateOptionsLength === 1;
    },
    displaySaveButton() {
      return this.displayTimeInForm || this.stepper > 1;
    },
    disableSaveButton() {
      return !this.eventDetailsComplete || this.eventDateOptionsLength === 0;
    },
    disableNextStepButton() {
      return !this.eventDetailsComplete;
    },
    /*
     * The one line the footer carries, as a message key, or nothing.
     *
     * The date step's instruction is spent the moment it is obeyed, and it is
     * read off eventDateOptionsLength — the same count disableSaveButton
     * reads, kept up to date by the date step's own added/deleted events — so
     * the hint and the save button can never disagree about whether the grid
     * holds anything.
     */
    footerHint() {
      if (this.stepper > 1) {
        return this.eventDateOptionsLength === 0 && 'agenda.datePoll.dragHint' || '';
      }
      return this.displayTimeInForm && 'agenda.datePoll.explanation' || '';
    },
    nextStepClass() {
      return this.displayTimeInForm && 'btn' || 'btn btn-primary';
    },
    stepButtonLabel() {
      return this.displayTimeInForm ? this.$t('agenda.alternativeDates') : this.$t('agenda.button.continue');
    },
    saveButtonLabel() {
      if (this.eventDateOptionsLength > 1) {
        return this.$t('agenda.label.schedule');
      } else if (this.event.id || this.event.parent) {
        return this.$t('agenda.label.save');
      } else {
        return this.$t('agenda.label.create');
      }
    },
  },
  watch: {
    event() {
      this.$agendaUtils.initEventForm(this.event);
    },
    eventDateOptionsLength(newVal, oldVal) {
      if (newVal !== oldVal) {
        if (newVal === 1 && oldVal > 1) {
          this.$root.$emit('alert-message', this.$t('agenda.datePollSwitchedToEvent'), 'info');
        } else if (oldVal === 1 && newVal > 1) {
          this.$root.$emit('alert-message', this.$t('agenda.eventSwitchedToDatePoll'), 'info');
        }
      }
    },
    eventDateOptions() {
      this.eventDateOptionsLength = this.event.dateOptions.length;
    },
    eventOwner() {
      if (this.eventOwner && this.eventOwner.remoteId && (!this.selectedCalendar || !this.selectedCalendar.owner || this.selectedCalendar.owner.remoteId !== this.eventOwner.remoteId)) {
        this.$identityService.getIdentityByProviderIdAndRemoteId(this.eventOwner.providerId, this.eventOwner.remoteId)
          .then(identity => {
            if (identity) {
              return this.$calendarService.getCalendars(0, 1, false, [Number(identity.id)]);
            }
          })
          .then(data => {
            this.selectedCalendar = data && data.calendars.length && data.calendars[0] || null;
          });
      } else if (!this.eventOwner || !this.eventOwner.remoteId) {
        this.selectedCalendar = null;
      }
    },
    stepper() {
      this.$agendaUtils.initEventForm(this.event);
      this.eventDateOptionsLength = this.event.dateOptions.length;

      this.$nextTick(() => {
        if (this.$refs.eventBasicInformation) {
          this.$refs.eventBasicInformation.reset();
        }
      });
    },
  },
  mounted() {
    this.reset();
  },
  methods: {
    close() {
      this.$emit('close');
    },
    updateDateOptionsLength() {
      this.eventDateOptionsLength = this.event.dateOptions.length;
    },
    /**
     * Puts the form back to the step the caller asked for.
     *
     * The stepper is driven to 0 first and only then to its target on the next
     * tick: the stepper watcher is what re-initialises the event and the
     * details step, and a v-model assigned its current value would not fire it.
     * Which target is the caller's choice — 1 for the details step, 2 when the
     * quick-add drawer already collected the details and asked for the date
     * step directly.
     *
     * @returns {void}
     */
    reset() {
      if (this.eventCalendar && this.eventCalendar.acl) {
        this.selectedCalendar = this.eventCalendar;
      } else {
        this.selectedCalendar = null;
      }

      this.$agendaUtils.initEventForm(this.event);
      this.eventDateOptionsLength = this.event.dateOptions.length;

      this.stepper = 0;
      this.$nextTick().then(() => this.stepper = this.openDateOptions && 2 || 1);
      this.$forceUpdate();
    },
    previousStep() {
      this.stepper--;
      this.$forceUpdate();
    },
    saveEvent() {
      this.event.start = this.event.startDate && this.$agendaUtils.toRFC3339(this.event.startDate) || this.$agendaUtils.toRFC3339(new Date());
      this.event.end = this.event.endDate && this.$agendaUtils.toRFC3339(this.event.endDate) || this.$agendaUtils.toRFC3339(new Date());
      this.event.calendar.owner.id = this.selectedCalendar.owner.id;
      this.$root.$emit('agenda-event-save', this.event);
    },
    nextStep() {
      if (this.stepper > 1) {
        this.saveEvent();
      } else if (this.stepper === 1) {
        if (this.$refs.eventBasicInformation.validateForm()) {
          this.stepper++;
        }
      } else {
        this.stepper = 1;
      }
    }
  }
};
</script>