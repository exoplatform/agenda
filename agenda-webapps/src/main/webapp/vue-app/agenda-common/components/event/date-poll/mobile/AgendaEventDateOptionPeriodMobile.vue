<template>
  <v-row class="d-flex flex-nowrap col-12">
    <v-col class="flex-grow-1 flex-shrink-0 d-flex my-auto">
      <agenda-event-date-option-vote />
    </v-col>
    <v-col class="flex-grow-1 flex-shrink-0 d-flex my-auto">
      <date-format
        :value="dateOption.start"
        :format="dateDayFormat"
        class="text-no-wrap mr-1 font-weight-bold" />
    </v-col>
    <v-col v-if="!sameDayDates" class="flex-grow-1 flex-shrink-0 my-auto">
      -
      <date-format
        :value="dateOption.end"
        :format="dateDayFormat"
        class="ml-1 font-weight-bold" />
    </v-col>
    <v-col v-if="dateOption.allDay" class="flex-grow-1 flex-shrink-0 d-flex my-auto">
      {{ $t('agenda.allDay') }}
    </v-col>
    <v-col v-else class="flex-grow-1 flex-shrink-0 d-flex my-auto">
      <date-format
        :value="dateOption.start"
        :format="dateTimeFormat"
        class="mr-1" />
      -
      <date-format
        :value="dateOption.end"
        :format="dateTimeFormat"
        class="ml-1 mr-2" />
    </v-col>
    <v-col class="flex-grow-1 flex-shrink-0 d-flex">
      <agenda-event-date-option-voter-mobile
        :voters="voters" />
    </v-col>
    <v-col class="flex-grow-0 flex-shrink-0">
      <v-btn
        :title="$t('agenda.finalDate')"
        icon
        right
        fab
        x-small>
        <v-icon color="#f8b441">fa-trophy</v-icon>
      </v-btn>
    </v-col>
  </v-row>
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
    voters: {
      type: Array,
      default: () => null,
    }
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