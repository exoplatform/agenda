<template>
  <v-snackbar
    :value="displayAlerts"
    color="transparent"
    elevation="0"
    absolute
    left>
    <agenda-notification-alert
      v-for="(alert, index) in alerts"
      :key="index"
      :alert="alert"
      @dismissed="deleteAlert(index)" />
  </v-snackbar>
</template>

<script>
export default {
  data: () => ({
    alerts: [],
  }),
  computed: {
    displayAlerts() {
      return this.alerts && this.alerts.length;
    },
  },
  created() {
    this.$root.$on('agenda-notification-alert', alert => this.alerts.push(alert));
  },
  methods: {
    deleteAlert(index) {
      this.alerts.splice(index, 1);
      this.$forceUpdate();
    }
  },
};
</script>
