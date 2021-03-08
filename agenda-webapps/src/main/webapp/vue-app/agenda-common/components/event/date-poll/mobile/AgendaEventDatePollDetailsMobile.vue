<template>
  <v-flex class="agenda-date-poll-details-mobile">
    <template>
      <v-list>
        <v-list-item>
          <v-list-item-content>
            <agenda-event-date-option-period
                v-for="(dateOption, index) in dateOptions"
                :key="index"
                :date-option="dateOption"
                :can-select="canSelectDate"
                :selected="selectedDateOptionIndex === index"
                @select="selectDate(index)"
                class="mb-5" />
            <template v-if="voters">
              <agenda-event-date-option-voter
                v-for="(voter, index) in voters"
                :key="index"
                :voter="voter"
                :date-options="dateOptions"
                :selected-date-index="selectedDateOptionIndex"
                :is-voting="isVoting"
                :event-creator-id="event.creator.id"
                @changed="enableVoteButton"
                @change-vote="isVoting = true" />
            </template>
            <v-list-item-action>
              <v-icon />
            </v-list-item-action>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </template>
  </v-flex>
</template>

<script>
export default {
  props: {
    dateOptions: {
      type: Object,
      default: () => null
    },
    voters: {
      type: Object,
      default: () => null
    },
    event: {
      type: Object,
      default: () => null
    },
    isVoting: {
      type: Boolean,
      default: false,
    },
    isCreator: {
      type: Boolean,
      default: false,
    },
  },
  data () {
    return {
      selectedDateOptionIndex: -1,
    }
  },
  computed: {
    canSelectDate() {
      return this.isCreator && !this.isVoting;
    },
    selectDate(index) {
      if (this.isCreator && !this.isVoting) {
        if (this.selectedDateOptionIndex === index) {
          this.selectedDateOptionIndex = -1;
        } else {
          this.selectedDateOptionIndex = index;
        }
      }
    },
  },
};
</script>