<template>
  <v-flex v-if="events && events.length" class="agenda-timeline pa-5">
    <template>
      <v-list
        v-for="month in eventsMonths"
        :key="month"
        class="agenda-timeline-month pa-0 ma-0"
        min-height="auto"
        min-width="100%"
        dense>
        <v-list-item class="agenda-timeline-month-title px-0" dense>
          <v-list-item-action-text class="text-capitalize">
            <date-format :value="month" :format="monthFormat" />
          </v-list-item-action-text>
          <v-list-item-content class="ms-2">
            <v-divider />
          </v-list-item-content>
        </v-list-item>
        <v-list-item
          v-for="eventDay in eventsDaysByMonth[month]"
          :key="eventDay"
          class="px-0"
          dense>
          <v-list-item-action
            :class="toDay === eventDay && 'primary--text'"
            class="align-self-start center event-timeline-day text-uppercase">
            <date-format :value="eventDay" :format="dayFormat" />
          </v-list-item-action>
          <v-list-item-content class="pa-0">
            <v-list class="pa-0">
              <v-list-item
                v-for="(event, i) in limitedEventsByDates[month][eventDay]"
                :key="i"
                :title="event.summary"
                :style="rowColorStyle(event)"
                :class="event.type === 'remoteEvent' && 'remote-event'"
                class="event-timeline-detail d-flex flex-column white--text px-2 py-0 mb-2 border-radius"
                dark
                @click="openEventDetails(event)">
                <v-list-item-content class="event-timeline-detail-content">
                  <!-- A row read live from a connected account is a row like
                       any other: it is painted with its calendar's colour and
                       written in white over it. The theme primary this used to
                       force here was picked when such a row was drawn on the
                       card's own background; over a colour it is the one row of
                       the list nobody can read. -->
                  <strong class="text-truncate">{{ event.summary }}</strong>
                  <div v-if="event.allDay">
                    {{ $t('agenda.allDay') }}
                  </div>
                  <div
                    v-else
                    class="d-flex flex-row">
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
                    <!-- the account the event belongs to, not "the"
                         connected one: several can be connected at once -->
                    <!-- white like the rest of the row when the identity is a
                         font icon; an uploaded logo keeps the white tile the
                         connector avatar draws behind an image -->
                    <agenda-connector-avatar
                      v-if="event.type === 'remoteEvent'"
                      :connector="event.connector || connectedConnector"
                      class="ms-auto me-1"
                      icon-class="white--text"
                      size="16" />
                  </div>
                </v-list-item-content>
              </v-list-item>
            </v-list>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </template>
  </v-flex>
  <agenda-empty-timeline v-else-if="!loading" :can-create-event="canCreateEvent" />
</template>
<script>
/*
 * The colour a row falls back to when neither the event nor its calendar
 * names one — agenda's own default blue, the same one the left edge of this
 * row has always used and the same one the calendar grid paints with
 * (AgendaCalendar.getEventBorderColor).
 */
const DEFAULT_EVENT_COLOR = '#2196F3';

export default {
  props: {
    events: {
      type: Array,
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
    connectedConnector: {
      type: Object,
      default: null
    },
    canCreateEvent: {
      type: Boolean,
      default: false,
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
      return Object.keys(this.limitedEventsByDates).sort((d1, d2) => new Date(d1).getTime() - new Date(d2).getTime());
    },
    eventsDaysByMonth() {
      if (!this.events || !this.events.length) {
        return {};
      }
      const eventsDaysByMonth = {};
      Object.keys(this.limitedEventsByDates).forEach(eventMonth => {
        eventsDaysByMonth[eventMonth] = Object.keys(this.limitedEventsByDates[eventMonth]).sort((d1, d2) => new Date(d1).getTime() - new Date(d2).getTime());
      });
      return eventsDaysByMonth;
    },
    eventsByDates() {
      if (!this.events || !this.events.length) {
        return {};
      }
      const eventsByDates = {};
      this.events.forEach(event => {
        const eventStartDate = JSON.parse(JSON.stringify(event));
        eventStartDate.startDate = new Date(event.startDate);
        eventStartDate.endDate = new Date(event.endDate);

        let periodStartDate = new Date(this.periodStartDate);
        periodStartDate = new Date(periodStartDate.getFullYear(), periodStartDate.getMonth(), periodStartDate.getDate());
        if (new Date(eventStartDate.startDate).getTime() > new Date(periodStartDate).getTime() || (new Date(eventStartDate.startDate).getTime() === new Date(periodStartDate).getTime() && event.allDay)) {
          this.addEventByDateInMap(eventStartDate, event.startDate, eventsByDates);
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
              this.addEventByDateInMap(eventAllDay, eventAllDay.startDate, eventsByDates);
              startOfDayOfNextStartDay.setDate(startOfDayOfNextStartDay.getDate() + 1);
            }
          }

          const eventEndDate = JSON.parse(JSON.stringify(event));
          eventEndDate.startDate = new Date(event.startDate);
          eventEndDate.endDate = new Date(event.endDate);
          eventEndDate.startsOnBeginningOfDay = true;
          this.addEventByDateInMap(eventEndDate, event.endDate, eventsByDates);
        }
      });
      return eventsByDates;
    },
    limitedEventsByDates() {
      if (!this.events?.length) {
        return {};
      }
      const limitedEventsByDates = {};
      let count = 0;
      for (const [month, days] of Object.entries(this.eventsByDates)) {
        for (const [day, events] of Object.entries(days)) {
          const remaining = this.limit - count;
          if (remaining <= 0) {
            return limitedEventsByDates;
          }
          const sliced = events.slice(0, remaining);
          if (sliced.length) {
            if (!limitedEventsByDates[month]) {
              limitedEventsByDates[month] = {};
            }
            limitedEventsByDates[month][day] = sliced;
            count += sliced.length;
          }
          if (count >= this.limit) {
            return limitedEventsByDates;
          }
        }
      }
      return limitedEventsByDates;
    },
  },
  created() {
    this.$root.$emit('agenda-event-limit-increment');
  },
  methods: {
    /**
     * The colour a row is painted and edged with: the event's own, else its
     * calendar's — the chain this list has always used.
     *
     * <p>
     * With two corrections a row written in white cannot do without. The first
     * is that the placeholder white Office 365 and Exchange write on every
     * event they return is not a colour: painted, it puts the row's white text
     * on a white ground, which is how a row of those accounts became
     * unreadable the moment it stopped forcing a colour of its own onto its
     * title. The second is that a row left with no colour at all takes the
     * same default the left edge has always taken, rather than falling through
     * to the card underneath — the second way to end up white on white, and
     * the one a connector reaches by declaring nothing rather than white.
     *
     * @param {Object} event the event the row draws
     * @returns {Object} the style binding for the row
     */
    rowColorStyle(event) {
      const color = this.$agendaUtils.paintableColor(event && event.color)
        || this.$agendaUtils.paintableColor(event && event.calendar && event.calendar.color)
        || DEFAULT_EVENT_COLOR;
      return {background: color, borderLeft: `5px solid ${color}`};
    },
    addEventByDateInMap(event, date, map) {
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
    },
    openEventDetails(event) {
      this.$root.$emit('agenda-event-details', event);
    }  
  }
};
</script>