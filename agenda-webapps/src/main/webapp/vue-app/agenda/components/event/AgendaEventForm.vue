<template>
  <v-card flat class="d-flex flex-column">
    <v-stepper v-model="stepper" class="d-flex flex-grow-1 flex-column">
      <v-stepper-header class="flex-grow-0 no-box-shadow border-bottom-color border-box-sizing">
        <v-stepper-step step="1" class="text-header-title">
          <span class="primary--text font-weight-regular">
            {{ $t('agenda.stepEventDetails') }}
          </span>
        </v-stepper-step>
        <v-divider />
        <v-stepper-step step="2" class="text-header-title">
          <span :class="stepper > 1 && 'primary--text' || ''" class="font-weight-regular">
            {{ $t('agenda.stepEventChooseDate') }}
          </span>
        </v-stepper-step>
        <v-btn
          class="my-auto mr-2"
          color="grey"
          icon
          dark
          @click="$emit('close')">
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
            :current-space="currentSpace" />
        </v-stepper-content>
        <v-stepper-content step="2">
          <agenda-event-form-dates
            ref="eventDates"
            :event="event"
            :weekdays="weekdays" />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
    <div class="d-flex flex-row flex-grow-0 mx-2 mx-md-10 my-2 mb-md-10">
      <v-btn
        v-if="stepper > 1"
        :disabled="saving"
        class="btn mr-2"
        @click="previousStep">
        <v-icon>mdi-chevron-left</v-icon>
        <span class="d-none d-md-inline">
          {{ $t('agenda.button.previous') }}
        </span>
      </v-btn>
      <div class="ml-auto d-flex flex-row">
        <v-btn
          :loading="saving"
          :disabled="saving"
          color="primary"
          @click="nextStep">
          {{ $t('agenda.button.continue') }}
        </v-btn>
        <v-btn
          :disabled="saving"
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
    event: {
      type: Object,
      default: () => ({}),
    },
    weekdays: {
      type: Array,
      default: () => null
    },
    currentSpace: {
      type: Object,
      default: () => null
    },
  },
  data () {
    return {
      stepper: 1,
      saving: false,
    };
  },
  mounted() {
    this.reset();
  },
  methods:{
    close() {
      this.$emit('close');
      window.setTimeout(() => {
        this.stepper = 1;
      }, 200);
    },
    reset() {
      this.stepper = 1;
      if (this.$refs.eventBasicInformation) {
        this.$refs.eventBasicInformation.reset();
      }
    },
    previousStep() {
      this.stepper--;
    },
    nextStep() {
      if (this.stepper > 1) {
        this.saving = true;
        const saveEventMethod = this.event.id ? this.$eventService.updateEvent:this.$eventService.createEvent;
        saveEventMethod(this.event)
          .then(() => this.$emit('saved'))
          .finally(() => {
            this.saving = false;
          });
      } else if (this.stepper === 1) {
        if (this.$refs.eventBasicInformation.validateForm()) {
          this.stepper++;
          this.$refs.eventDates.resetEvents();
        }
      } else {
        this.stepper = 1;
      }
    }
  }
};
</script>