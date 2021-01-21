<template>
  <v-col
    :title="dateOptionHeaderTitle"
    :class="dateOptionHeaderClass"
    cols="2"
    @click="$emit('select')">
    <v-card
      :dark="selected"
      class="pa-2 event-date-options-cell"
      outlined
      tile>
      <v-card-title class="pa-2 justify-center">
        <div class="d-inline-flex">
          <date-format
            :value="dateOption.start"
            :format="dateDayFormat"
            class="mr-1" />
          <template v-if="!sameDayDates">
            -
            <date-format
              :value="dateOption.end"
              :format="dateDayFormat"
              class="ml-1" />
          </template>
        </div>
      </v-card-title>
      <v-card-text class="pa-2 d-flex justify-center">
        <template v-if="dateOption.allDay">
          {{ $t('agenda.allDay') }}
        </template>
        <template v-else>
          <date-format
            :value="dateOption.start"
            :format="dateTimeFormat"
            class="mr-1" />
          -
          <date-format
            :value="dateOption.end"
            :format="dateTimeFormat"
            class="ml-1 mr-2" />
        </template>
      </v-card-text>
      <v-fab-transition>
        <v-btn
          v-show="selected"
          :title="$t('agenda.finalDate')"
          absolute
          top
          right
          icon
          fab>
          <v-icon color="#f8b441">fa-trophy</v-icon>
        </v-btn>
      </v-fab-transition>
    </v-card>
  </v-col>
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
    selected: {
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
    dateOptionHeaderTitle() {
      return this.canSelect && this.$t('agenda.clickToSelectDate') || '';
    },
    dateOptionHeaderClass() {
      let dateOptionHeaderClass = '';
      if (this.canSelect) {
        dateOptionHeaderClass += 'clickable';
      }
      if (this.selected) {
        dateOptionHeaderClass += ' event-date-option-header-selected';
      }
      return dateOptionHeaderClass;
    },
  },
};
</script>