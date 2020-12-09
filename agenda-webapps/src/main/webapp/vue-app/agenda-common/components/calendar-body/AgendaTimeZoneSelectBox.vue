<template>
  <div id="AgendaTimeZone" class="d-flex">
    <select
      v-model="selectedTimeZone"
      class="subtitle-1 ignore-vuetify-classes">
      <option
        v-for="timeZone in timezones"
        :key="timeZone.value"
        :value="timeZone.value">
        {{ timeZone.text }}
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
  },
  data() {
    return {
      selectedTimeZone: this.$agendaUtils.USER_TIMEZONE_ID,
      timeZoneChoices: [],
    };
  },
  created() {
    this.$root.$emit('send-timezone',this.selectedTimeZone);
  },
  methods:{
    changeTimeZone(value){
      this.selectedTimeZone = value;
    }
  }
};
</script>