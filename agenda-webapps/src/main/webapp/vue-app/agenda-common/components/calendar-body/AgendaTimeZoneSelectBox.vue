<template>
  <div id="AgendaTimeZone" class="d-flex">
    <select
      v-model="selectedTimeZone"
      class="subtitle-1 ignore-vuetify-classes timeZoneSelectBox"
      @change="$root.$emit('send-timezone',selectedTimeZone)">
      <option
        v-for="timeZone in timezones"
        :key="timeZone.value"
        :value="timeZone.value">
        {{ timeZone.value }}
      </option>
    </select>
  </div>
</template>

<script>
export default {
  props: {
    timezones: {
      type: Array,
      default: () => null,
    },
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      selectedTimeZone: null,
      timeZoneChoices: [],
    };
  },
  created() {
    this.selectedTimeZone = this.event.timeZoneId;
  }
};
</script>