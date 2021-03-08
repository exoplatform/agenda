<template>
  <v-list-item
    :style="eventStyle"
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
            :format="fullDateFormat"
            class="mr-1" />
          <template v-if="!sameDayDates">
            -
            <date-format
              :value="event.end"
              :format="fullDateFormat"
              class="ml-1" />
          </template>
        </div>
        <div :style="textStyle" class="d-inline-flex">
          <template v-if="event.allDay">
            {{ $t('agenda.allDay') }}
          </template>
          <template v-else>
            <date-format
              :value="event.start"
              :format="dateTimeFormat"
              class="ml-1 mr-1" />
            -
            <date-format
              :value="event.end"
              :format="dateTimeFormat"
              class="ml-1 mr-2" />
          </template>
        </div>
      </v-list-item-subtitle>
    </v-list-item-content>
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
  data:() => ({
    fullDateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
    dateDayFormat: {
      month: 'short',
      day: 'numeric',
    },
    dateTimeFormat: {
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
    eventStyle() {
      if (this.event) {
        return `background: ${this.eventColor};`;
      } else {
        return `border: 1px solid ${this.eventColor};`;
      }
    },
    eventColor() {
      if (this.event) {
        if (this.event.color) {
          return this.event.color;
        } else {
          return this.event.calendar.color;
        }
      }
      return '';
    },
  },
};
</script>