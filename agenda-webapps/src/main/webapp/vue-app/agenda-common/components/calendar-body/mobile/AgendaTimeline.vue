<template>
  <v-flex class="agenda-timeline">
    <template v-if="events && events.length">
      <v-list
        v-for="month in eventsMonths"
        :key="month"
        class="agenda-timeline-month pa-0 ma-0"
        min-height="auto"
        min-width="100%"
        dense>
        <v-list-item class="agenda-timeline-month-title" dense>
          <v-list-item-action class="event-timeline-day" />
          <v-list-item-action-text class="subtitle-1 me-2 text-capitalize">
            <date-format :value="month" :format="monthFormat" />
          </v-list-item-action-text>
          <v-list-item-content>
            <v-divider />
          </v-list-item-content>
        </v-list-item>
        <v-list-item
          v-for="eventDay in eventsDaysByMonth[month]"
          :key="eventDay"
          dense>
          <v-list-item-action
            :class="toDay === eventDay && 'primary--text'"
            class="align-self-start center event-timeline-day text-uppercase">
            <date-format :value="eventDay" :format="dayFormat" />
          </v-list-item-action>
          <v-list-item-content class="pa-0">
            <v-list class="pa-0">
              <v-list-item
                v-for="(event, i) in eventsByDates[month][eventDay]"
                :key="i"
                :title="event.summary"
                :style="{background: event.color || event.calendar.color}"
                class="event-timeline-detail d-flex flex-column white--text px-2 py-0 mb-2 border-radius"
                dark
                @click="$root.$emit('agenda-event-details', event)">
                <v-list-item-content class="event-timeline-detail-content">
                  <strong class="text-truncate">{{ event.summary }}</strong>
                  <div v-if="event.allDay">
                    {{ $t('agenda.allDay') }}
                  </div>
                  <div v-else class="d-flex flex-row">
                    <div v-if="event.startsOnBeginningOfDay">
                      {{ $t('agenda.beginningOfTheDay') }}
                    </div>
                    <date-format
                      v-else
                      :value="event.startDate"
                      :format="timeFormat" />
                    <strong class="mx-2">-</strong>
                    <div v-if="event.endsOnEndOfDay">
                      {{ $t('agenda.endOfTheDay') }}
                    </div>
                    <date-format
                      v-else
                      :value="event.endDate"
                      :format="timeFormat" />
                  </div>
                </v-list-item-content>
              </v-list-item>
            </v-list>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </template>
    <agenda-empty-timeline v-else-if="!loading" />
  </v-flex>
</template>
<script>
export default {
  props: {
    events: {
      type: Object,
      default: null,
    },
    periodStartDate: {
      type: Object,
      default: null,
    },
    limit: {
      type: Number,
      default: 0,
    },
    loading: {
      type: Boolean,
      default: false,
    },
    agendaBaseLink: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    monthFormat: {
      month: 'long',
      year: 'numeric',
    },
    dayFormat: {
      day: 'numeric',
      weekday: 'short',
    },
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
    toDay() {
      const toDay = new Date();
      return String(new Date(toDay.getFullYear(), toDay.getMonth(), toDay.getDate()));
    },
    eventsMonths() {
      return Object.keys(this.eventsByDates).sort((d1, d2) => new Date(d1).getTime() - new Date(d2).getTime());
    },
    eventsDaysByMonth() {
      if (!this.events || !this.events.length) {
        return {};
      }
      const eventsDaysByMonth = {};
      Object.keys(this.eventsByDates).forEach(eventMonth => {
        eventsDaysByMonth[eventMonth] = Object.keys(this.eventsByDates[eventMonth]).sort((d1, d2) => new Date(d1).getTime() - new Date(d2).getTime());
      });
      return eventsDaysByMonth;
    },
    eventsByDates() {
      if (!this.events || !this.events.length) {
        return {};
      }
      const eventsByDates = {};
      let count = 0;
      this.events.forEach(event => {
        const eventStartDate = JSON.parse(JSON.stringify(event));
        eventStartDate.startDate = new Date(event.startDate);
        eventStartDate.endDate = new Date(event.endDate);

        let periodStartDate = new Date(this.periodStartDate);
        periodStartDate = new Date(periodStartDate.getFullYear(), periodStartDate.getMonth(), periodStartDate.getDate());
        if (new Date(eventStartDate.startDate).getTime() > new Date(periodStartDate).getTime()) {
          count = this.addEventByDateInMap(eventStartDate, event.startDate, eventsByDates, count);
        }

        if (!this.$agendaUtils.areDatesOnSameDay(event.startDate, event.endDate)) {
          eventStartDate.endsOnEndOfDay = true;

          const startDate = new Date(event.startDate);
          const endDate = new Date(event.endDate);

          const startOfDayOfNextStartDay = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate() + 1);
          const startOfDayOfNextEndDay = new Date(endDate.getFullYear(), endDate.getMonth(), endDate.getDate() + 1);

          const daysNumbers = (startOfDayOfNextEndDay.getTime() - startOfDayOfNextStartDay.getTime()) / 86400000;
          if (daysNumbers > 1) {
            for (let i = 1; i < daysNumbers; i++) {
              const eventAllDay = JSON.parse(JSON.stringify(event));
              eventAllDay.startDate = new Date(startOfDayOfNextStartDay);
              eventAllDay.endDate = new Date(startOfDayOfNextStartDay);
              eventAllDay.allDay = true;
              if (periodStartDate.getTime() > eventAllDay.startDate.getTime()) {
                continue;
              }
              count = this.addEventByDateInMap(eventAllDay, eventAllDay.startDate, eventsByDates, count);
              startOfDayOfNextStartDay.setDate(startOfDayOfNextStartDay.getDate() + 1);
            }
          }

          const eventEndDate = JSON.parse(JSON.stringify(event));
          eventEndDate.startDate = new Date(event.startDate);
          eventEndDate.endDate = new Date(event.endDate);
          eventEndDate.startsOnBeginningOfDay = true;
          count = this.addEventByDateInMap(eventEndDate, event.endDate, eventsByDates, count);
        }
      });
      return eventsByDates;
    },
  },
  created() {
    this.$root.$emit('agenda-event-limit-increment');
  },
  methods: {
    addEventByDateInMap(event, date, map, count) {
      if (++count > this.limit) {
        return --count;
      }
      const monthDate = new Date(date.getFullYear(), date.getMonth());
      if (!map[monthDate]) {
        map[monthDate] = {};
      }
      const dayDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
      if (!map[monthDate][dayDate]) {
        map[monthDate][dayDate] = [event];
      } else {
        map[monthDate][dayDate].push(event);
      }
      return count;
    },
  }
};
</script>