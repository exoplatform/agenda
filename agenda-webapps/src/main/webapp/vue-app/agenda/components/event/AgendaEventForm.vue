<template>
  <v-stepper v-model="stepper">
    <v-stepper-header>
      <v-stepper-step step="1" class="text-header-title" color="primary">
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
        @click="closeDialog"
      >
        <v-icon>
          mdi-close
        </v-icon>
      </v-btn>
    </v-stepper-header>
    <v-stepper-items>
      <v-stepper-content step="1">
        <agenda-event-details-form class="mx-4" />
        <v-layout row justify-end>
          <v-btn text>
            Cancel
          </v-btn>
          <v-btn color="primary" class="mr-5" @click="nextStep">
            Continue
          </v-btn>
        </v-layout>
      </v-stepper-content>
      <v-stepper-content step="2">
        <v-card
          height="200px"
        />
        <v-layout row justify-end>
          <v-btn
            color="primary"
            @click="nextStep"
          >
            Continue
          </v-btn>
          <v-btn text>
            Cancel
          </v-btn>
        </v-layout>
      </v-stepper-content>
    </v-stepper-items>
  </v-stepper>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({})
    },
  },
  data () {
    return {
      stepper: 1
    };
  },
  methods:{
    closeDialog() {
      this.$emit('close');
    },
    nextStep() {
      if (this.stepper < 2) {
        this.stepper ++;
      } else {
        this.stepper = 1;
      }
    }
  }
};
</script>