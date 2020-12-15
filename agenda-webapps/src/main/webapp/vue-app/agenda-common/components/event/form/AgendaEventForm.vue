<template>
  <v-card flat class="d-flex flex-column">
    <v-stepper
      v-if="event"
      v-model="stepper"
      class="d-flex flex-grow-1 flex-column">
      <v-stepper-header class="flex-grow-0 no-box-shadow border-bottom-color border-box-sizing">
        <v-stepper-step step="1">
          <small class="primary--text title">
            {{ $t('agenda.stepEventDetails') }}
          </small>
        </v-stepper-step>
        <v-divider />
        <v-stepper-step step="2">
          <small class="title" :class="stepper > 1 && 'primary--text' || ''">
            {{ $t('agenda.stepEventChooseDate') }}
          </small>
        </v-stepper-step>
        <v-btn
          class="my-auto mr-2"
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
            :settings="settings"
            :connectors="connectors"
            :conference-provider="conferenceProvider"
            :current-space="currentSpace"
            @next-step="nextStep"
            @initialized="$emit('initialized')" />
        </v-stepper-content>
        <v-stepper-content step="2">
          <agenda-event-form-dates
            v-if="stepper === 2"
            ref="eventDates"
            :settings="settings"
            :event="event"
            :connectors="connectors"
            :weekdays="weekdays"
            :working-time="workingTime" />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
    <v-divider />
    <div class="d-flex flex-grow-0 my-2">
      <v-btn
        v-if="stepper > 1"
        class="btn mr-2"
        @click="previousStep">
        <v-icon>mdi-chevron-left</v-icon>
        <span class="d-none d-md-inline">
          {{ $t('agenda.button.previous') }}
        </span>
      </v-btn>
      <div class="ml-auto mr-10">
        <v-btn
          :disabled="disableSaveButton"
          class="btn btn-primary"
          @click="nextStep">
          {{ stepButtonLabel }}
        </v-btn>
        <v-btn
          class="btn ml-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
      </div>
    </div>
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
      stepper: 1,
    };
  },
  computed: {
    eventTitle() {
      return this.event && this.event.summary;
    },
    eventTitleValid() {
      return this.eventTitle && this.eventTitle.length >= 5 && this.eventTitle.length < 1024;
    },
    eventOwner() {
      return this.event && this.event.calendar && this.event.calendar.owner;
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
    disableSaveButton() {
      return !this.eventTitleValid || !this.eventOwnerValid || !this.eventDescriptionValid;
    },
    stepButtonLabel() {
      return this.stepper === 2 ? this.$t('agenda.button.save') : this.$t('agenda.button.continue');
    },
  },
  mounted() {
    this.reset();
  },
  methods:{
    close() {
      this.$emit('close');
    },
    reset() {
      this.stepper = 1;
    },
    previousStep() {
      this.stepper--;
    },
    nextStep() {
      if (this.stepper > 1) {
        this.event.start = this.event.startDate && this.$agendaUtils.toRFC3339(this.event.startDate) || this.$agendaUtils.toRFC3339(new Date());
        this.event.end = this.event.endDate && this.$agendaUtils.toRFC3339(this.event.endDate) || this.$agendaUtils.toRFC3339(new Date());

        this.$root.$emit('agenda-event-save', this.event);
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