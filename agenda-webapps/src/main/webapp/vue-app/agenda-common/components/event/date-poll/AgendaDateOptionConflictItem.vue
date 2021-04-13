<template>
  <v-list-item
    :style="eventStyle"
    two-line
    dense
    class="pending-invitation-item border-radius">
    <v-list-item-content class="pa-0">
      <v-list-item-title
        :style="textStyle"
        class="white--text text-truncate subtitle-1">
        {{ event.summary }}
      </v-list-item-title>
      <v-list-item-subtitle class="text-truncate d-flex caption mt-1">
        <div :style="textStyle" class="d-inline-flex">
          <date-format
            :value="event.start"
            :format="dateFormat"
            class="me-1" />
          <template v-if="!sameDayDates">
            -
            <date-format
              :value="event.end"
              :format="dateFormat"
              class="ms-1" />
          </template>
        </div>
        <div :style="textStyle" class="d-inline-flex">
          <template v-if="event.allDay">
            {{ $t('agenda.allDay') }}
          </template>
          <template v-else>
            <date-format
              :value="event.start"
              :format="timeFormat"
              class="ms-1 me-1" />
            -
            <date-format
              :value="event.end"
              :format="timeFormat"
              class="ms-1 me-2" />
          </template>
        </div>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action
      v-if="isDatePoll"
      :title="$t('agenda.votedDatePollOption')"
      class="my-1 py-0">
      <i
        :style="textStyle"
        :class="eventIcon"
        class="uiIcon32x32 uiIconPLFFont align-self-center d-flex mb-1 me-1"></i>
    </v-list-item-action>
  </v-list-item>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({})
    },
  },
  data: () => ({
    dateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
    textStyle() {
      if (this.event) {
        return 'color: #fff !important';
      } else {
        return `color: ${this.eventColor} !important`;
      }
    },
    sameDayDates() {
      return this.event.start && this.event.end && this.$agendaUtils.areDatesOnSameDay(this.event.start, this.event.end);
    },
    eventColor() {
      return this.event && this.event.calendar && this.event.calendar.color || '#2196F3';
    },
    eventStyle() {
      return `background: ${this.eventColor};`;
    },
    isDatePoll() {
      return this.event.status === 'TENTATIVE';
    },
    eventIcon() {
      return this.isDatePoll && 'uiIconStatistics' || 'uiIconCalendarEmpty';
    },
  },
};
</script>