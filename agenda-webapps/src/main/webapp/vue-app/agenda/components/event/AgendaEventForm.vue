<template>
  <v-stepper v-model="stepper">
    <v-stepper-header>
      <v-stepper-step step="1" class="text-header-title">
        {{ $t('agenda.stepEventDetails') }}
      </v-stepper-step>
      <v-divider />
      <v-stepper-step step="2" class="text-header-title">
        {{ $t('agenda.stepEventChooseDate') }}
      </v-stepper-step>
      <v-btn
        class="mt-5"
        color="grey"
        icon
        dark
        @click="closeDialog">
        <v-icon>
          mdi-close
        </v-icon>
      </v-btn>
    </v-stepper-header>
    <v-stepper-items>
      <v-stepper-content step="1">
        <agenda-event-form-basic-information :event="event" class="mx-4" />
        <div
          class="d-flex flex-row justify-md-end">
          <v-btn text>
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            class="mr-5"
            @click="nextStep">
            Continue
          </v-btn>
        </div>
      </v-stepper-content>
      <v-stepper-content step="2">
        <v-flex>
          <agenda-event-form-dates :event="event" />
        </v-flex>
        <div class="d-flex flex-row justify-md-end">
          <v-btn
            :loading="saving"
            :disabled="saving"
            color="primary"
            @click="nextStep">
            Continue
          </v-btn>
          <v-btn text @click="close">
            Cancel
          </v-btn>
        </div>
      </v-stepper-content>
    </v-stepper-items>
  </v-stepper>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data () {
    return {
      stepper: 1,
      saving: false,
    };
  },
  methods:{
    closeDialog() {
      this.$emit('close');
    },
    nextStep() {
      if (this.stepper > 1) {
        this.saving = true;
        this.$eventService.createEvent(this.event)
          .then(() => this.$emit('saved'))
          .finally(() => {
            this.saving = false;
          });
      } else if (this.stepper === 1) {
        this.stepper++;
      } else {
        this.stepper = 1;
      }
    }
  }
};
</script>