<template>
  <v-card flat class="d-flex flex-column">
    <v-stepper
      v-if="event"
      v-model="stepper"
      class="d-flex flex-grow-1 flex-column">
      <v-stepper-header class="flex-grow-0 no-box-shadow border-bottom-color border-box-sizing">
        <div class="d-flex flex-grow-1">
          <v-stepper-step :complete="stepper > 1" step="1">
            <small class="primary--text title">
              {{ $t('agenda.stepEventDetails') }}
            </small>
          </v-stepper-step>
          <v-divider class="eventFormStepperDivider" />
          <v-stepper-step step="2">
            <small class="title" :class="stepper > 1 && 'primary--text' || ''">
              {{ $t('agenda.stepEventChooseDate') }}
            </small>
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
  },
  data () {
    return {
      eventDateOptionsLength: 0,
      selectedCalendar: null,
      stepper: 0,
    };
  },
  computed: {
    eventTitle() {
      return this.event && this.event.summary;
    },
    eventTitleValid() {
      return this.eventTitle && this.eventTitle.length >= 5 && this.eventTitle.length < 1024;
    },
    eventCalendar() {
      return this.event && this.event.calendar;
    },
    eventOwner() {
      return this.eventCalendar && this.eventCalendar.owner;
    },
    eventOwnerValid() {
      return this.eventOwner && (this.eventOwner.id || this.eventOwner.remoteId && this.eventOwner.providerId);
    },
    eventDescription() {
      return this.event && this.event.description || '';
    },
    eventDescriptionValid() {
      return this.eventDescription.length <= 1300;
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
      return !this.eventTitleValid || !this.eventOwnerValid || !this.eventDescriptionValid || this.eventDateOptionsLength === 0;
    },
    disableNextStepButton() {
      return !this.eventTitleValid || !this.eventOwnerValid || !this.eventDescriptionValid;
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
          this.$root.$emit('agenda-notification-alert', {
            message: this.$t('agenda.datePollSwitchedToEvent'),
            type: 'info',
          }, 'event-form');
        } else if (oldVal === 1 && newVal > 1) {
          this.$root.$emit('agenda-notification-alert', {
            message: this.$t('agenda.eventSwitchedToDatePoll'),
            type: 'info',
          }, 'event-form');
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
    reset() {
      if (this.eventCalendar && this.eventCalendar.acl) {
        this.selectedCalendar = this.eventCalendar;
      } else {
        this.selectedCalendar = null;
      }

      this.$agendaUtils.initEventForm(this.event);
      this.eventDateOptionsLength = this.event.dateOptions.length;

      this.stepper = 0;
      this.$nextTick().then(() => this.stepper = 1);
      this.$forceUpdate();
    },
    previousStep() {
      this.stepper--;
      this.$forceUpdate();
    },
    saveEvent() {
      this.event.start = this.event.startDate && this.$agendaUtils.toRFC3339(this.event.startDate) || this.$agendaUtils.toRFC3339(new Date());
      this.event.end = this.event.endDate && this.$agendaUtils.toRFC3339(this.event.endDate) || this.$agendaUtils.toRFC3339(new Date());

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