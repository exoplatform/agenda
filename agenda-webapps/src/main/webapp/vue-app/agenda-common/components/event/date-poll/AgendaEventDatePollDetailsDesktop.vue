<template>
  <div class="d-flex flex-row pa-6">
    <v-spacer />
    <div class="d-inline">
      <table
        id="event-date-options-table"
        description="Event date options table"
        class="event-date-options-table mx-auto">
        <caption>
          <em v-if="event.description">
            <div class="text-center font-italic subtitle-1 text-wrap pb-4">
              {{ event.description }}
            </div>
          </em>
        </caption>
        <tr>
          <th id="participantsTitle" class="event-date-options-cell justify-center">
            <v-card
              class="d-flex fill-height border-box-sizing"
              flat>
              <v-card-title class="ma-auto text-no-wrap text-center">
                {{ $t('agenda.participants') }}
              </v-card-title>
            </v-card>
          </th>
          <agenda-event-date-option-period
            v-for="(dateOption, index) in dateOptions"
            :key="index"
            :date-option="dateOption"
            :can-select="canSelectDate"
            :selected="selectedDateOptionIndex === index"
            @select="$emit('selectDate', index)" />
        </tr>
        <tr>
          <th id="participantsCount" class="event-date-options-cell justify-center">
            <v-card
              class="d-flex fill-height border-box-sizing"
              flat>
              <v-card-text class="ma-auto text-center text-no-wrap">
                {{ votedAttendeesCount }}
                /
                {{ attendeesCount }}
                {{ $t('agenda.participants') }}
              </v-card-text>
            </v-card>
          </th>
          <th
            v-for="(dateOption, index) in dateOptions"
            :id="`dateOption_${index}`"
            :key="index"
            :class="selectedDateOptionIndex === index && 'event-date-option-cell-selected' || ''"
            class="event-date-options-cell">
            <v-card
              class="d-flex fill-height text-center border-box-sizing"
              flat>
              <v-card-text class="ma-auto text-center">
                {{ dateOption.voters && dateOption.voters.length || 0 }}
              </v-card-text>
            </v-card>
          </th>
        </tr>
        <template v-if="voters">
          <agenda-event-date-option-voter
            v-for="(voter, index) in voters"
            :key="index"
            :voter="voter"
            :date-options="dateOptions"
            :selected-date-index="selectedDateOptionIndex"
            :is-voting="isVoting"
            :event-creator-id="event.creator.id"
            @changed="$emit('enableVoteButton')" />
        </template>
      </table>
    </div>
    <v-spacer />
  </div>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null
    },
    dateOptions: {
      type: Array,
      default: () => null
    },
    voters: {
      type: Array,
      default: () => null
    },
    selectedDateOptionIndex: {
      type: Number,
      default: () => -1
    },
    votedAttendeesCount: {
      type: Number,
      default: () => -1
    },
    attendeesCount: {
      type: Number,
      default: () => -1
    },
    canSelectDate: {
      type: Boolean,
      default: false,
    },
    isVoting: {
      type: Boolean,
      default: false,
    },
  },
};
</script>