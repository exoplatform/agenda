<template>
  <v-stepper v-model="stepper">
    <v-stepper-header>
      <v-stepper-step step="1">
        {{ $t('agenda.stepEventDetails') }}
      </v-stepper-step>
      <v-divider />
      <v-stepper-step step="2">
        {{ $t('agenda.stepEventChooseDate') }}
      </v-stepper-step>
      <v-btn
        color="grey"
        icon
        dark
        @click="closeDialog"
      >
        <v-icon class="mt-9">mdi-close</v-icon>
      </v-btn>
    </v-stepper-header>
    <v-stepper-items>
      <v-stepper-content step="1">
        <agenda-event-details-form />
        <v-row>
          <v-col />
          <v-col />
          <v-col>
            <v-btn text>
              Cancel
            </v-btn>
            <v-btn color="primary" @click="nextStep">
              Continue
            </v-btn>
          </v-col>
        </v-row>
      </v-stepper-content>
      <v-stepper-content step="2">
        <v-card
          class="mb-12"
          color="grey lighten-1"
          height="200px"
        />

        <v-btn
          color="primary"
          @click="nextStep"
        >
          Continue
        </v-btn>
        <v-btn text>
          Cancel
        </v-btn>
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