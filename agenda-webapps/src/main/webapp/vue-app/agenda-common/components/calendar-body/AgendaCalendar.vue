<template>
  <v-calendar
    ref="calendar"
    v-model="selectedDate"
    :events="eventsToDisplay"
    :event-color="getEventColor"
    :event-text-color="getEventTextColor"
    :event-timed="isEventTimed"
    :type="calendarType"
    :weekdays="weekdays"
    :interval-style="agendaIntervalStyle"
    :interval-height="40"
    :event-overlap-threshold="30"
    :locale="lang"
    event-overlap-mode="stack"
    event-name="summary"
    event-start="startDate"
    event-end="endDate"
    event-ripple="acl.canEdit"
    color="primary"
    show-week
    @click:event="showEvent"
    @click:more="showDay"
    @click:date="showDay"
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
        :style="currentTimeStyle">
      </div>
    </template>
    <template #event="{ event, timed, eventSummary }">
      <div :class="getEventClass(event)">
        <strong
          :title="event.summary"
          class="text-truncate my-auto d-flex ml-2">
          <div class="flex-grow-1 text-truncate">
            {{ event.summary }}
          </div>
          <div v-if="isEventTentative(event)" class="flex-grow-0 mr-1">
            <i class="uiIcon attendee-response attendee-response-tentative"></i>
          </div>
        </strong>
        <div
          v-if="event && !event.allDay && !isShortEvent(event)"
          class="v-event-draggable px-2 d-flex flex-row">
          <date-format
            :value="event.startDate"
            :format="timeFormat"
            class="v-event-draggable mr-2" />
          <strong class="mx-2">-</strong>
          <date-format
            :value="event.endDate"
            :format="timeFormat"
            class="v-event-draggable mr-2" />
        </div>
      </div>
      <div
        v-if="timed && canEdit(event)"
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
    currentCalendar: {
      type: Object,
      default: () => null
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
    nowDate: null,
    lang: eXo.env.portal.language,
    dragEvent: null,
    dragDelta: null,
    mouseDown: false,
    mouseIsPressed: 0,
    eventExtended: false,
    eventDragged: false,
    selectedDate: '',
    selectedEvent: {},
    selectedElement: null,
    selectedOpen: false,
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    }
  }),
  computed: {
    // A workaround to display events that finishes at midnight the same day
    eventsToDisplay() {
      const eventsToDisplay = [];
      this.events.forEach(event => {
        if (event.endDate && event.endDate.toString().indexOf('00:00:00') >= 0) {
          const eventToDisplay = JSON.parse(JSON.stringify(event));
          eventToDisplay.startDate = this.$agendaUtils.toDate(event.startDate);
          eventToDisplay.endDate = this.$agendaUtils.toDate(event.endDate);
          eventToDisplay.endDate = new Date(eventToDisplay.endDate.getTime() - 60000);
          eventsToDisplay.push(eventToDisplay);
        } else {
          eventsToDisplay.push(event);
        }
      });
      return eventsToDisplay;
    },
    nowTimeOptions() {
      return this.nowDate && {hour: this.nowDate.getHours(), minute: this.nowDate.getMinutes()};
    },
    currentTimeTop() {
      return this.nowDate && this.$refs && this.$refs.calendar && this.nowTimeOptions && this.$refs.calendar.timeToY(this.nowTimeOptions);
    },
    currentTimeStyle() {
      return this.nowDate && this.currentTimeTop && `top: ${this.currentTimeTop}px;`;
    },
    canCreateEvent() {
      return !this.currentCalendar || !this.currentCalendar.acl || this.currentCalendar.acl.canCreate;
    },
  },
  watch: {
    mouseIsPressed() {
      if (!this.mouseIsPressed && this.mouseDown) {
        this.mouseDown = false;
        this.cancelEventModification();
      }
    },
    mouseDown(newVal, oldVal) {
      if (!newVal && oldVal) {
        window.setTimeout(() => {
          if (!this.saving) {
            this.cancelEventModification();
          }
        }, 500);
      }
    },
    calendarType() {
      this.scrollToTime();
    },
    workingTime() {
      this.scrollToTime();
    }
  },
  created() {
    document.body.onmousedown = () => ++this.mouseIsPressed;
    document.body.onmouseup = () => --this.mouseIsPressed;
  },
  mounted() {
    this.$root.$on('agenda-display-calendar-atDate', date => {
      this.selectedDate = date || '';
    });
    this.$root.$on('agenda-display-calendar-next', () => {
      if (this.$refs.calendar) {
        this.$refs.calendar.next();
      }
    });
    this.$root.$on('agenda-display-calendar-previous', () => {
      if (this.$refs.calendar) {
        this.$refs.calendar.prev();
      }
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
    this.$root.$on('agenda-event-save-error', () => {
      this.saving = false;
      this.cancelEventModification();
    });
    this.$root.$on('agenda-event-save-opened', () => this.saving = true);
    this.$root.$on('agenda-event-save-cancel', () => {
      this.saving = false;
      this.cancelEventModification();
    });
    this.$root.$on('agenda-event-quick-form-cancel', () => {
      this.saving = false;
      this.cancelEventModification();
    });
    document.body.onmouseleave = () => {
      this.cancelEventModification();
    };

    this.nowDate = new Date();
    this.$nextTick().then(() => this.scrollToTime());

    window.setTimeout(() => {
      // Refresh current time each 3 minutes
      const dailyScrollElement = document.querySelector('.v-calendar-daily__scroll-area');
      const REFRESH_PERIOD = 60000;
      if (dailyScrollElement) {
        window.setInterval(() => {
          this.nowDate = new Date();
        }, REFRESH_PERIOD);
      }
    }, 2000);
  },
  methods: {
    getEventClass(event) {
      const textStyle = this.isEventDeclined(event) && ' text-decoration-line-through' || '';
      const editModeStyle = this.canEdit(event) && 'editable-event' || 'readonly-event';
      return `${editModeStyle}${textStyle}`;
    },
    canEdit(event) {
      return event && event.acl && event.acl.canEdit;
    },
    scrollToTime() {
      this.$nextTick().then(() => {
        const dailyScrollElement = document.querySelector('.v-calendar-daily__scroll-area');
        if (dailyScrollElement) {
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

      const period = this.$agendaUtils.convertVuetifyRangeToPeriod(range);
      period.title = this.$refs.calendar.title;
      this.$root.$emit('agenda-change-period', period);
    },
    getEventTextColor(event) {
      const eventColor = event && (event.color || event.calendar && event.calendar.color) || '#2196F3';
      if (!event.acl || !event.acl.attendee) {
        return eventColor;
      }
      const currentUserAttendee = event.attendees && event.attendees.find(attendee => attendee.identity.id === eXo.env.portal.userIdentityId);
      if (!currentUserAttendee || currentUserAttendee.response === 'DECLINED' || currentUserAttendee.response === 'NEEDS_ACTION' || currentUserAttendee.response === 'TENTATIVE') {
        return eventColor;
      }
      return 'white';
    },
    isEventDeclined(event) {
      const currentUserAttendee = event.attendees && event.attendees.find(attendee => attendee.identity.id === eXo.env.portal.userIdentityId);
      return currentUserAttendee && currentUserAttendee.response === 'DECLINED';
    },
    isEventTentative(event) {
      const currentUserAttendee = event.attendees && event.attendees.find(attendee => attendee.identity.id === eXo.env.portal.userIdentityId);
      return currentUserAttendee && currentUserAttendee.response === 'TENTATIVE';
    },
    getEventColor(event) {
      if (!event.acl || !event.acl.attendee) {
        return 'white';
      }
      const currentUserAttendee = event.attendees && event.attendees.find(attendee => attendee.identity.id === eXo.env.portal.userIdentityId);
      if (!currentUserAttendee || currentUserAttendee.response === 'DECLINED' || currentUserAttendee.response === 'NEEDS_ACTION' || currentUserAttendee.response === 'TENTATIVE') {
        return 'white';
      }
      const eventColor = event && (event.color || event.calendar && event.calendar.color) || '#2196F3';
      if (this.$agendaUtils.toDate(event.endDate).getTime() > Date.now()) {
        return eventColor;
      } else {
        return this.$agendaUtils.addOpacity(eventColor, 40);
      }
    },
    isEventTimed(event) {
      return event && !event.allDay;
    },
    showEvent(eventObj) {
      if (this.eventDragged || this.eventExtended) {
        this.cancelEventModification();
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
    showDay(eventObj) {
      if (eventObj.nativeEvent) {
        eventObj.nativeEvent.preventDefault();
        eventObj.nativeEvent.stopPropagation();
      }
      this.cancelEventModification();
      this.$root.$emit('agenda-display-calendar-atDate', eventObj.date);
      this.$root.$emit('agenda-change-period-type', 'day');
    },
    extendEventEndDate(eventObj) {
      const dragEvent = eventObj && eventObj.event || eventObj;
      if (!dragEvent || !dragEvent.acl || !dragEvent.acl.canEdit) {
        return;
      }
      this.eventExtended = true;
      this.dragEvent = dragEvent;
      if (this.dragEvent) {
        this.originalDragedEvent = JSON.parse(JSON.stringify(this.dragEvent));
        this.originalDragedEvent.startDate = this.$agendaUtils.toDate(this.dragEvent.startDate);
        this.originalDragedEvent.endDate = this.$agendaUtils.toDate(this.dragEvent.endDate);
      }
    },
    eventMouseDown(eventObj) {
      if (eventObj && eventObj.nativeEvent) {
        eventObj.nativeEvent.preventDefault();
        eventObj.nativeEvent.stopPropagation();
      }

      this.mouseDown = true;
      window.setTimeout(() => {
        if (this.mouseDown) {
          const dragEvent = eventObj && eventObj.event || eventObj;
          if (!dragEvent || !dragEvent.acl || !dragEvent.acl.canEdit) {
            return;
          }
          this.dragEvent = dragEvent;
          if (this.dragEvent) {
            this.originalDragedEvent = JSON.parse(JSON.stringify(this.dragEvent));
            this.originalDragedEvent.startDate = this.$agendaUtils.toDate(this.dragEvent.startDate);
            this.originalDragedEvent.endDate = this.$agendaUtils.toDate(this.dragEvent.endDate);
          }
        }
      }, 500);
    },
    eventMouseUp(eventObj) {
      this.mouseDown = false;
      if (this.eventDragged || this.eventExtended) {
        if (eventObj && eventObj.nativeEvent) {
          eventObj.nativeEvent.preventDefault();
          eventObj.nativeEvent.stopPropagation();
        }
        this.saveDraggedEvent();
      }
    },
    eventMouseMove(params) {
      if (this.dragEvent) {
        if (this.eventExtended) {
          const newDate = this.$agendaUtils.toDateTime(params, false);
          if (newDate >= this.$agendaUtils.toDate(this.dragEvent.startDate).getTime()) {
            this.dragEvent.endDate = this.$agendaUtils.toDateTime(params, true);
          } else {
            this.dragEvent.endDate = this.dragEvent.startDate;
          }
        } else {
          const newTime = this.$agendaUtils.toDateTime(params, false);
          if (!Number.isNaN(newTime)) {
            this.eventDragged = true;

            const start = this.$agendaUtils.toDate(this.dragEvent.startDate).getTime();
            const end = this.$agendaUtils.toDate(this.dragEvent.endDate).getTime();
            const duration = end - start;
            const newStartTime = newTime - this.dragDelta;
            const newStart = this.$agendaUtils.roundTime(newStartTime);
            const newEnd = newStart + duration;
            this.dragEvent.startDate = this.$agendaUtils.toDate(newStart);
            this.dragEvent.endDate = this.$agendaUtils.toDate(newEnd);
          }
        }
      }
    },
    calendarMouseDown(params) {
      if (!params || params.event || !this.canCreateEvent) {
        return;
      }
      if (this.dragEvent) {
        if (!this.dragDelta) {
          this.dragDelta = this.$agendaUtils.toDateTime(params) - this.$agendaUtils.toDate(this.dragEvent.startDate).getTime();
        }
      } else if (!this.eventDragged && !this.eventExtended) {
        this.cancelEventModification();

        const startDate = this.$agendaUtils.toDateTime(params, true);
        this.quickEvent = {
          summary: '',
          startDate: startDate,
          endDate: startDate + this.$agendaUtils.MINIMUM_TIME_INTERVAL_MS,
          allDay: !params.time,
          editing: true,
          calendar: {
            owner: {},
          },
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
        const newDate = this.$agendaUtils.toDateTime(params);
        if (this.$agendaUtils.toDate(this.quickEvent.startDate).getTime() > newDate) {
          this.quickEvent.startDate = this.$agendaUtils.toDateTime(params, false);
        } else {
          this.quickEvent.endDate = this.$agendaUtils.toDateTime(params, true);
        }

        if (!this.quickEvent.added) {
          this.quickEvent.added = true;
          this.events.push(this.quickEvent);
        }
      }
    },
    calendarMouseUp() {
      this.mouseDown = false;
      if (this.quickEvent) {
        if (!this.quickEvent.added) {
          this.quickEvent.added = true;
          this.events.push(this.quickEvent);
        }
        delete this.quickEvent.editing;

        window.setTimeout(() => {
          if (this.quickEvent) {
            this.saving = true;
            this.$root.$emit('agenda-event-quick-form', this.quickEvent);
          }
        }, 200);
      } else if (this.eventDragged || this.eventExtended) {
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
            return;
          }
        } else {
          if (this.$agendaUtils.toRFC3339(this.dragEvent.startDate) === this.$agendaUtils.toRFC3339(this.originalDragedEvent.startDate)
              && this.$agendaUtils.toRFC3339(this.dragEvent.endDate) === this.$agendaUtils.toRFC3339(this.originalDragedEvent.endDate)) {
            return;
          }
        }
      }

      if (this.dragEvent) {
        this.saving = true;
        const retrieveEvent = this.dragEvent.id ?
          this.$eventService.getEventById(this.dragEvent.id, 'all')
          :this.$eventService.getEventOccurrence(this.dragEvent.parent.id, this.dragEvent.occurrence.id, 'all');
        return retrieveEvent.then(event => {
          event.start = this.$agendaUtils.toRFC3339(this.dragEvent.startDate);
          event.end = this.$agendaUtils.toRFC3339(this.dragEvent.endDate);
          event.timeZoneId = this.$agendaUtils.USER_TIMEZONE_ID;
          event.allDay = this.dragEvent.allDay;

          // when this is about a recurrent event
          // and the event is an all day event,
          // when moving event, it shouldn't
          const ignoreRecurrentPopin = event.allDay;
          const changeDatesOnly = true;
          this.$root.$emit('agenda-event-save', event, ignoreRecurrentPopin, changeDatesOnly);
        });
      } else {
        this.cancelEventModification();
      }
    },
    applyEventModification() {
      this.originalDragedEvent = null;
      this.saving = false;
      this.cancelEventModification();
    },
    cancelEventModification() {
      if (this.saving) {
        return;
      }

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
        this.dragEvent.startDate = this.$agendaUtils.toDate(this.originalDragedEvent.start);
        this.dragEvent.endDate = this.$agendaUtils.toDate(this.originalDragedEvent.end);
      }
      this.eventDragged = false;
      this.eventExtended = false;
      this.dragDelta = null;
      this.dragEvent = null;
      this.originalDragedEvent = null;
    },
    agendaIntervalStyle(interval) {
      if (this.workingTime.showWorkingTime) {
        if (this.workingTime.workingTimeStart && this.workingTime.workingTimeEnd) {
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
    },
    isShortEvent(event) {
      return this.$agendaUtils.isShortEvent(event);
    },
  }
};
</script>
