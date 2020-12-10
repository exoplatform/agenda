<template>
  <v-autocomplete
    id="AgendaTimeZone"
    v-model="event.timeZoneId"
    :items="timeZones"
    item-text="value"
    class="subtitle-1 pa-0"
    dense
    filled />
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      timeZones: [],
    };
  },
  created() {
    this.timeZones = this.$agendaUtils.getTimeZones();
  }
};
</script>