<template>
  <v-card flat class="d-flex flex-column">
    <v-stepper v-model="stepper" class="d-flex flex-grow-1 flex-column">
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
            :current-space="currentSpace"
            @next-step="nextStep" />
        </v-stepper-content>
        <v-stepper-content step="2">
          <agenda-event-form-dates
            v-if="stepper === 2"
            ref="eventDates"
            :event="event"
            :weekdays="weekdays"
            @next-step="nextStep" />
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
          :disabled="disableSaveButton"
          class="btn btn-primary"
          @click="nextStep">
          {{ stepButtonLabel }}
        </v-btn>
        <v-btn
          :disabled="saving"
          class="btn ml-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
      </div>
    </div>
    <agenda-recurrent-event-save-confirm-dialog
      ref="recurrentEventConfirm"
      :event="event"
      @save-event="save" />
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
  computed: {
    eventTitle() {
      return this.event && this.event.summary;
    },
    eventTitleValid() {
      return this.eventTitle && this.eventTitle.length >= 5 && this.eventTitle.length < 1024;
    },
    eventOwnerId() {
      return this.event && this.event.calendar && this.event.calendar.owner && this.event.calendar.owner.id;
    },
    disableSaveButton() {
      return this.saving || !this.eventTitleValid || !this.eventOwnerId;
    },
    stepButtonLabel() {
      return this.stepper === 2 ? this.$t('agenda.button.save') : this.$t('agenda.button.continue');
    },
  },
  created() {
    this.$root.$on('agenda-event-form', () => {
      this.reset();
    });
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
    },
    previousStep() {
      this.stepper--;
    },
    save(eventToSave) {
      this.saving = true;
      const saveEventMethod = eventToSave.id ? this.$eventService.updateEvent:this.$eventService.createEvent;
      saveEventMethod(eventToSave)
        .then(() => this.$emit('saved'))
        .finally(() => {
          this.saving = false;
        });
    },
    nextStep() {
      if (this.stepper > 1) {
        if (this.event.occurrence) {
          this.$refs.recurrentEventConfirm.open();
        } else {
          this.save(this.event);
        }
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