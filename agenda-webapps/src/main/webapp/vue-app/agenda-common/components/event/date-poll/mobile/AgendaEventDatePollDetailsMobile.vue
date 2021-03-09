<template>
  <v-flex class="agenda-date-poll-details-mobile">
    <v-list>
      <v-list-item>
        <v-list-item-content>
          <agenda-event-date-option-period-mobile
            v-for="(dateOption, index) in dateOptions"
            :key="index"
            :date-option="dateOption"
            :can-select="canSelectDate"
            :voters="voters" />
        </v-list-item-content>
      </v-list-item>
    </v-list>
  </v-flex>
</template>

<script>
export default {
  props: {
    dateOptions: {
      type: Object,
      default: () => null
    },
    canSelectDate: {
      type: Boolean,
      default: false,
    },
    voters: {
      type: Array,
      default: () => null,
    }
  }
};
</script>