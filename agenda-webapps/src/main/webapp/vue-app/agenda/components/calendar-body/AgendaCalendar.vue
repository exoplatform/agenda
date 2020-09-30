<template>
  <v-calendar
    ref="calendar"
    v-model="selectedDate"
    :events="events"
    :event-color="getEventColor"
    :event-timed="isEventTimed"
    :type="calendarType"
    :weekdays="weekdays"
    :interval-style="agendaIntervalStyle"
    event-name="summary"
    event-start="startDate"
    event-end="endDate"
    color="primary"
    show-week
    @click:event="showEvent"
    @click:more="viewDay"
    @click:date="viewDay"
    @mousedown:event="eventMouseDown"
    @mousemove:event="eventMouseMove"
    @mouseup:event="eventMouseUp"
    @mousedown:time="calendarMouseDown"
    @mousemove:time="calendarMouseMove"
    @mouseup:time="calendarMouseUp"
    @mousedown:day="calendarMouseDown"
    @mousemove:day="calendarMouseMove"
    @mouseup:day="calendarMouseUp"
    @contextmenu:event="cancelEventModification"
    @contextmenu:time="cancelEventModification"
    @contextmenu:day="cancelEventModification"
    @change="retrievePeriodEvents">
    <template #day-body="day">
      <div
        class="v-current-time"
        :class="{ today: day.present }"
        :style="currentTimeStyle"></div>
    </template>
    <template #event="{ event, timed, eventSummary }">
      <div v-if="event" class="v-event-draggable d-flex flex-row">
        <strong class="text-truncate">{{ event.summary }}</strong>
        <template v-if="!event.allDay">
          <date-format
            :value="event.startDate"
            :format="timeFormat"
            class="v-event-draggable ml-2" />
          <strong class="mx-2">-</strong>
          <date-format
            :value="event.endDate"
            :format="timeFormat"
            class="v-event-draggable mr-2" />
        </template>
      </div>
      <div
        v-if="timed"
        class="v-event-drag-bottom"
        @mousedown.stop="extendEventEndDate(event)"></div>
    </template>
  </v-calendar>
</template>

<script>
export default {
  props: {
    events: {
      type: Array,
      default: null
    },
    calendarType: {
      type: String,
      default: null
    },
    weekdays: {
      type: Array,
      default: () => null
    },
    workingTime: {
      type: Object,
      default: () => null
    },
  },
  data: () => ({
    saving: false,
    quickEvent: null,
    originalDragedEvent: null,
    dragEvent: null,
    dragDelta: null,
    eventExtended: false,
    eventDragged: false,
    selectedDate: '',
    selectedEvent: {},
    selectedElement: null,
    selectedOpen: false,
    currentTimeTop: null,
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    }
  }),
  computed: {
    nowTimeOptions() {
      const now = new Date();
      return {hour: now.getHours(), minute: now.getMinutes()};
    },
    currentTimeStyle() {
      return `top: ${this.currentTimeTop}px;`;
    }
  },
  watch: {
    calendarType() {
      this.scrollToTime();
    },
    workingTime() {
      this.scrollToTime();
    }
  },
  mounted() {
    this.$root.$on('agenda-display-calendar-atDate', date => {
      this.selectedDate = date || '';
    });
    this.$root.$on('agenda-display-calendar-next', () => {
      this.$refs.calendar.next();
    });
    this.$root.$on('agenda-display-calendar-previous', () => {
      this.$refs.calendar.prev();
    });
    this.$root.$on('agenda-calendar-color-changed', (calendarId, calendarColor) => {
      this.events.forEach(event => {
        if (event.calendar.id === calendarId) {
          event.calendar.color = calendarColor;
        }
      });
      this.$forceUpdate();
    });
    this.$root.$on('agenda-event-saved', this.applyEventModification);
    this.$root.$on('agenda-event-save-cancel', this.cancelEventModification);
    this.$root.$on('agenda-event-quick-form-cancel', this.cancelEventModification);
    this.scrollToTime();
  },
  methods:{
    scrollToTime() {
      this.$nextTick().then(() => {
        const dailyScrollElement = document.querySelector('.v-calendar-daily__scroll-area');
        if (dailyScrollElement) {
          this.currentTimeTop = this.$refs.calendar.timeToY(this.nowTimeOptions);
          const scrollY = this.currentTimeTop - dailyScrollElement.offsetHeight / 2;
          dailyScrollElement.scrollTo(0, scrollY);
        }
      });
    },
    retrievePeriodEvents(range) {
      // In Vuetify, the 'start object' === 'end object',
      // this is a workaround to avoid changing end date
      // and impact start date as well, when we are in day view
      range.end = JSON.parse(JSON.stringify(range.end));

      // End of the day of end date
      range.end.hour = 23;
      range.end.minute = 59;

      const period = this.$agendaUtils.convertVuetifyRangeToPeriod(range, this.$userTimeZone);
      period.title = this.$refs.calendar.title;
      this.$root.$emit('agenda-change-period', period);
    },
    getEventColor(event) {
      return event.color || event.calendar && event.calendar.color || 'primary';
    },
    isEventTimed(event) {
      return event && !event.allDay;
    },
    showEvent(eventObj) {
      if (this.eventDragged || this.eventExtended) {
        return;
      }
      this.cancelEventModification();
      const event = eventObj && eventObj.event || eventObj;
      if (!event.id && !event.occurrence) {
        return;
      }
      if (eventObj.nativeEvent) {
        eventObj.nativeEvent.preventDefault();
        eventObj.nativeEvent.stopPropagation();
      }
      this.$root.$emit('agenda-event-details', event);
      return false;
    },
    viewDay(eventObj) {
      if (eventObj.nativeEvent) {
        eventObj.nativeEvent.preventDefault();
        eventObj.nativeEvent.stopPropagation();
      }
      this.cancelEventModification();
      this.focus = eventObj.date;
      this.$root.$emit('agenda-change-period-type', 'day');
    },
    extendEventEndDate(eventObj) {
      this.eventExtended = true;
      this.dragEvent = eventObj && eventObj.event || eventObj;
      if (this.dragEvent) {
        this.originalDragedEvent = JSON.parse(JSON.stringify(this.dragEvent));
        this.originalDragedEvent.startDate = new Date(this.dragEvent.startDate);
        this.originalDragedEvent.endDate = new Date(this.dragEvent.endDate);
      }
    },
    eventMouseDown(eventObj) {
      this.dragEvent = eventObj && eventObj.event || this.dragEvent;
      if (this.dragEvent) {
        this.originalDragedEvent = JSON.parse(JSON.stringify(this.dragEvent));
        this.originalDragedEvent.startDate = new Date(this.dragEvent.startDate);
        this.originalDragedEvent.endDate = new Date(this.dragEvent.endDate);
      }
    },
    eventMouseUp() {
      if(this.eventDragged || this.eventExtended) {
        this.saveDraggedEvent();
      }
    },
    eventMouseMove(params) {
      if (this.dragEvent) {
        if (this.eventExtended) {
          const newDate = this.toDate(params, false);
          if (newDate >= this.dragEvent.startDate) {
            this.dragEvent.endDate = this.toDate(params, true);
          } else {
            this.dragEvent.endDate = this.dragEvent.startDate;
          }
        } else {
          const newTime = this.toDate(params, false).getTime();
          if (!Number.isNaN(newTime)) {
            this.eventDragged = true;

            const start = new Date(this.dragEvent.startDate).getTime();
            const end = new Date(this.dragEvent.endDate).getTime();
            const duration = end - start;
            const newStartTime = newTime - this.dragDelta;
            const newStart = this.roundTime(newStartTime);
            const newEnd = newStart + duration;
            this.dragEvent.startDate = new Date(newStart);
            this.dragEvent.endDate = new Date(newEnd);
          }
        }
      }
    },
    calendarMouseDown(params) {
      if (!params || params.event) {
        return;
      }
      if (this.dragEvent) {

        if (!this.dragDelta) {
          this.dragDelta = this.toDate(params).getTime() - new Date(this.dragEvent.startDate).getTime();
        }
      } else if (!this.eventDragged && !this.eventExtended) {
        this.cancelEventModification();

        const startDate = this.toDate(params, true);
        this.quickEvent = {
          summary: '',
          startDate: startDate,
          endDate: startDate,
          allDay: !params.time,
          editing: true,
          calendar: {
            owner: {},
          },
          reminders: [],
          attachments: [],
          attendees: [],
        };
      }
    },
    calendarMouseMove(params) {
      if (this.dragEvent) {
        this.eventMouseMove(params);
      }

      if (this.quickEvent && this.quickEvent.editing) {
        const newDate = this.toDate(params);
        if (new Date(this.quickEvent.startDate).getTime() > newDate.getTime()) {
          this.quickEvent.startDate = this.toDate(params, false);
        } else {
          this.quickEvent.endDate = this.toDate(params, true);
        }

        if (!this.quickEvent.added) {
          this.quickEvent.added = true;
          this.events.push(this.quickEvent);
        }
      }
    },
    calendarMouseUp() {
      if (this.quickEvent) {
        if (!this.quickEvent.added) {
          this.quickEvent.added = true;
          this.events.push(this.quickEvent);
        }
        delete this.quickEvent.editing;

        window.setTimeout(() => {
          if (this.quickEvent) {
            this.$root.$emit('agenda-event-quick-form-open', this.quickEvent);
          }
        }, 200);
      } else if(this.eventDragged || this.eventExtended) {
        this.saveDraggedEvent();
      }
    },
    saveDraggedEvent() {
      if (this.saving) {
        return;
      }

      if (this.originalDragedEvent) {
        if (this.dragEvent.allDay) {
          if (this.$agendaUtils.areDatesOnSameDay(this.dragEvent.startDate, this.originalDragedEvent.startDate)
              && this.$agendaUtils.areDatesOnSameDay(this.dragEvent.endDate, this.originalDragedEvent.endDate)) {
            this.cancelEventModification();
            return;
          }
        } else {
          if (new Date(this.dragEvent.startDate).getTime() === new Date(this.originalDragedEvent.startDate).getTime()
              && new Date(this.dragEvent.endDate).getTime() === new Date(this.originalDragedEvent.endDate).getTime()) {
            this.cancelEventModification();
            return;
          }
        }
      }

      if (this.dragEvent) {
        this.saving = true;
        const eventId = this.dragEvent.id || this.dragEvent.parent.id;
        return this.$eventService.getEventById(eventId, 'all')
          .then(event => {
            event.id = this.dragEvent.id;
            event.parent = this.dragEvent.parent;
            event.occurrence = this.dragEvent.occurrence;
            event.recurrence = null;

            event.start = this.$agendaUtils.toRFC3339(this.dragEvent.startDate);
            event.end = this.$agendaUtils.toRFC3339(this.dragEvent.endDate);
            event.allDay = this.dragEvent.allDay;

            this.$root.$emit('agenda-event-save', event, event.allDay);
          })
          .finally(() => this.saving = false);
      } else {
        this.cancelEventModification();
      }
    },
    applyEventModification() {
      this.originalDragedEvent = null;
      this.cancelEventModification();
    },
    cancelEventModification() {
      if (this.quickEvent) {
        const index = this.events.findIndex(event => event === this.quickEvent);
        if (index >= 0) {
          this.events.splice(index, 1);
        }
        this.quickEvent = null;
      } else {
        this.cancelEventDrag();
      }
    },
    cancelEventDrag() {
      if (this.saving) {
        return;
      }

      if (this.dragEvent && this.originalDragedEvent && !this.saving) {
        this.dragEvent.startDate = new Date(this.originalDragedEvent.startDate);
        this.dragEvent.endDate = new Date(this.originalDragedEvent.endDate);
      }
      this.eventDragged = false;
      this.eventExtended = false;
      this.dragDelta = null;
      this.dragEvent = null;
      this.originalDragedEvent = null;
    },
    roundTime(minute, down) {
      const roundTo = 15; // minutes

      return down
        ? minute - minute % roundTo
        : minute + (roundTo - minute % roundTo);
    },
    toDate(tms, down = true) {
      return new Date(tms.year, tms.month - 1, tms.day, tms.hour, this.roundTime(tms.minute, down));
    },
    agendaIntervalStyle(interval) {
      if (this.workingTime.showWorkingTime) {
        if(this.workingTime.workingTimeStart && this.workingTime.workingTimeEnd) {
          const inactive = interval.weekday === 0 ||
              interval.weekday === 6 ||
              interval.time < this.workingTime.workingTimeStart ||
              interval.time >= this.workingTime.workingTimeEnd;
          const startOfHour = interval.minute === 0;
          const dark = this.dark;
          const mid = dark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)';

          return {
            backgroundColor: inactive ? dark ? 'rgba(0,0,0,0.4)' : 'rgba(0,0,0,0.05)' : null,
            borderTop: startOfHour ? null : `1px dashed ${mid}`,
          };
        }
      } else {
        return null;
      }
    }
  }
};
</script>
