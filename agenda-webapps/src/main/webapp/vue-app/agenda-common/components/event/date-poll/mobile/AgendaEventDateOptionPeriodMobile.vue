<template>
  <div
    v-if="dateOption"
    class="d-flex flex-nowrap">
    <div class="d-inline-flex font-weight-bold">
      <date-format
        :value="dateOption.start"
        :format="dateDayFormat"
        class="text-no-wrap me-1" />
      <template v-if="!sameDayDates">
        -
        <date-format
          :value="dateOption.end"
          :format="dateDayFormat"
          class="ms-1" />
      </template>
    </div>
    <template v-if="dateOption.allDay">
      {{ $t('agenda.allDay') }}
    </template>
    <template v-else>
      <date-format
        :value="dateOption.start"
        :format="dateTimeFormat"
        class="me-1" />
      -
      <date-format
        :value="dateOption.end"
        :format="dateTimeFormat"
        class="ms-1 me-2" />
    </template>
  </div>
</template>

<script>
export default {
  props: {
    dateOption: {
      type: Object,
      default: () => null
    },
    canSelect: {
      type: Boolean,
      default: false,
    },
  },
  data () {
    return {
      currentYear: `${new Date().getYear() + 1900}`,
      dateShortDayFormat: {
        month: 'long',
        day: 'numeric',
      },
      dateFullDayFormat: {
        year: 'numeric',
        month: 'numeric',
        day: 'numeric',
      },
      dateTimeFormat: {
        hour: '2-digit',
        minute: '2-digit',
      },
    };
  },
  computed: {
    startThisYear() {
      return this.currentYear === this.dateOption.start.substring(0, 4);
    },
    endThisYear() {
      return this.currentYear === this.dateOption.end.substring(0, 4);
    },
    sameDayDates() {
      return this.dateOption.start.substring(0, 10) === this.dateOption.end.substring(0, 10);
    },
    dateDayFormat() {
      return this.startThisYear && this.endThisYear && this.dateShortDayFormat || this.dateFullDayFormat;
    },
  },
};
</script>