<template>
  <v-flex class="event-form-dates">
    <v-toolbar flat class="border-color mb-4">
      <div
        v-if="connectedAccountName">
        <v-avatar tile size="24">
          <img
            :alt="connectedAccount.name"
            :src="connectedAccountIconSource">
        </v-avatar>
        <a
          class="mx-2"
          @click="openPersonalCalendarDrawer">
          {{ connectedAccountName }}
        </a>
      </div>
      <v-btn
        v-else
        class="btn "
        @click="openPersonalCalendarDrawer">
        <i class="uiIconHyperlink darkGreyIcon" />
        {{ $t('agenda.connectYourPersonalAgenda') }}
      </v-btn>
      <v-row
        align="center"
        justify="center"
        class="flex-nowrap">
        <v-btn
          icon
          @click="prevDate">
          <i class="uiIconArrowLeft uiIconMedium darkGreyIcon"></i>
        </v-btn>
        <div class="period-title text-uppercase">
          {{ periodTitle }}
        </div>
        <v-btn
          icon
          color="grey darken-2"
          @click="nextDate">
          <i class="uiIconArrowRight uiIconMedium darkGreyIcon"></i>
        </v-btn>
      </v-row>
    </v-toolbar>
    <v-calendar
      ref="calendar"
      v-model="dayToDisplay"
      :events="displayedEvents"
      :event-color="getEventColor"
      :event-timed="isEventTimed"
      :weekdays="weekdays"
      :interval-style="agendaIntervalStyle"
      :event-ripple="false"
      event-name="summary"
      event-start="startDate"
      event-end="endDate"
      color="primary"
      type="week"
      @click:event="showEvent"
      @mousedown:event="showEvent"
      @mousedown:time="startTime"
      @mousemove:time="mouseMove"
      @mouseup:time="endDrag"
      @mouseleave:time="endDrag"
      @change="retrieveEvents">
      <template #event="eventObj">
        <div :id="getEventDomId(eventObj)" class="v-event-draggable v-event-draggable-parent">
          <strong class="text-truncate ml-2">{{ eventObj.event.summary }}</strong>
          <div class="d-flex">
            <template v-if="!eventObj.event.allDay">
              <date-format
                :value="eventObj.event.startDate"
                :format="timeFormat"
                class="ml-2" />
              <strong class="mx-2">-</strong>
              <date-format
                :value="eventObj.event.endDate"
                :format="timeFormat"
                class="mr-2" />
            </template>
          </div>
        </div>
      </template>
      <template #day-body="day">
        <div
          class="v-current-time"
          :class="{ today: day.present }"
          :style="currentTimeStyle"></div>
      </template>
    </v-calendar>
    <v-menu
      ref="eventDatesMenu"
      v-model="selectedOpen"
      :close-on-content-click="false"
      :activator="selectedElement"
      :left="menuLeftPosition"
      :right="!menuLeftPosition"
      :top="menuTopPosition"
      content-class="select-date-pickers agenda-application"
      offset-x>
      <v-card
        min-width="350"
        class="pa-0 pb-4"
        flat>
        <v-card-text class="pa-0">
          <div class="d-flex">
            <v-btn
              class="ml-auto"
              color="grey"
              icon
              dark
              @click="selectedOpen = false">
              <v-icon>
                mdi-close
              </v-icon>
            </v-btn>
          </div>
          <agenda-event-form-date-pickers
            v-if="selectedOpen"
            ref="selectedEventDatePickers"
            :event="selectedEvent"
            :date-picker-top="datePickerTop"
            class="px-3"
            @changed="updateCalendarDisplay(selectedEvent)" />
        </v-card-text>
      </v-card>
    </v-menu>
    <agenda-user-connected-account-drawer
      :connected-account="connectedAccount"
      :connectors="connectors" />
  </v-flex>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    weekdays: {
      type: Array,
      default: () => null
    },
    workingTime: {
      type: Object,
      default: () => null
    },
    connectors: {
      type: Array,
      default: () => null
    },
  },
  data: () => ({
    periodTitle: '',
    dragEvent: null,
    dragStart: null,
    createEvent: null,
    createStart: null,
    extendOriginal: null,
    selectedEvent: null,
    selectedElement: null,
    selectedOpen: null,
    datePickerTop: true,
    displayedEvents: [],
    dayToDisplay: Date.now(),
    dateTimeFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
    currentTimeTop: null,
    scrollToTimeTop: null,
    menuLeftPosition: false,
    menuTopPosition: false,
    connectedAccount: {},
  }),
  computed: {
    nowTimeOptions() {
      const now = new Date();
      return {hour: now.getHours(), minute: now.getMinutes()};
    },
    currentTimeStyle() {
      return `top: ${this.currentTimeTop}px;`;
    },
    events() {
      return this.event && [this.event] || [];
    },
    domId() {
      return `eventForm-${this.event.id}-${new Date(this.event.startDate).getTime()}`;
    },
    connectedAccountName() {
      return this.connectedAccount && this.connectedAccount.userId || '';
    },
    connectedAccountIconSource() {
      return this.connectedAccount && this.connectedAccount.icon || '';
    },
  },
  watch: {
    selectedOpen() {
      if (this.selectedOpen && this.$refs.selectedEventDatePickers) {
        this.$refs.selectedEventDatePickers.reset();
      }
    },
  },
  created() {
    if (!this.event.startDate) {
      this.event.startDate = this.event.start && this.$agendaUtils.toDate(this.event.start) || new Date();
      this.event.startDate = this.roundTime(new Date(this.event.startDate).getTime());
    }
    if (!this.event.endDate) {
      if (this.event.end) {
        this.event.endDate = this.$agendaUtils.toDate(this.event.end).getTime();
      } else {
        this.event.endDate = new Date(this.event.startDate).getTime();
      }
    }
    this.$calendarService.getAgendaConnectorsSettings().then(connectorSettings => {
      if (connectorSettings && connectorSettings.value) {
        this.connectedAccount = JSON.parse(connectorSettings.value);
      }
    });
  },
  mounted() {
    if (this.$refs.calendar) {
      this.currentTimeTop = this.$refs.calendar.timeToY(this.nowTimeOptions);
      this.scrollToEvent(this.event);
      window.setTimeout(() => {
        this.showEventDatePickers(this.event);
      }, 200);
    }
    this.$root.$on('agenda-event-save', () => {
      this.selectedEvent = null;
      this.selectedOpen = false;
    });
  },
  methods: {
    updateCalendarDisplay(event) {
      event.startDate = new Date(this.event.startDate);
      event.endDate = new Date(this.event.endDate);
      event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      event.end = this.$agendaUtils.toRFC3339(this.event.endDate);

      this.scrollToEvent(event);
      this.retrieveEvents();
      if (this.$refs.eventDatesMenu) {
        this.showEventDatePickers(event, 200);
      }
    },
    scrollToEvent(event) {
      const dateTime = this.$agendaUtils.toDate(event.startDate);
      this.scrollToTimeTop = this.$refs.calendar.timeToY({
        hour: dateTime.getHours(),
        minute: dateTime.getMinutes(),
      });
      this.dayToDisplay = event.startDate ? this.$agendaUtils.toDate(event.startDate).getTime(): this.$agendaUtils.toDate(event.start).getTime();
      this.$refs.calendar.updateTimes();
      this.scrollToTime();
    },
    scrollToTime() {
      this.$nextTick().then(() => {
        const dailyScrollElement = document.querySelector('.v-calendar-daily__scroll-area');
        if (dailyScrollElement) {
          const scrollY = this.scrollToTimeTop - dailyScrollElement.offsetHeight / 2;
          dailyScrollElement.scrollTo(0, scrollY);
        }
      });
    },
    showEvent({nativeEvent, event}) {
      if (!nativeEvent) {
        return;
      }
      nativeEvent.preventDefault();
      nativeEvent.stopPropagation();

      this.showEventDatePickers(event);
    },
    showEventDatePickers(event, waitTimeToDisplay = 200) {
      const domId = this.getEventDomId(event);
      let $targetElement = $(`#${domId}`);
      const targetElement = $targetElement.length && $targetElement[0];

      this.newEventStarted = false;
      this.selectedOpen = false;
      this.selectedElement = targetElement;
      this.selectedEvent = event;

      window.setTimeout(() => {
        if (this.selectedEvent) {
          const domId = this.getEventDomId(event);
          $targetElement = $(`#${domId}`);
          if (!this.selectedElement) {
            this.selectedElement = $targetElement.length && $targetElement[0];
          }
          if ($targetElement && $targetElement.length && $targetElement.offset()) {
            this.datePickerTop = $targetElement.offset().top > 330;
            this.menuTopPosition = window.innerHeight - $targetElement.offset().top < 400;
            this.menuLeftPosition = $targetElement.offset().left > window.innerWidth / 2;
          }
          this.selectedOpen = true;
        } else {
          this.selectedOpen = false;
        }
      }, waitTimeToDisplay);
    },
    startTime(tms) {
      const mouse = this.toTime(tms);
      this.event.startDate = this.roundTime(mouse);
      this.event.endDate = this.$agendaUtils.toDate(this.event.startDate);
      this.newEventStarted = true;
      this.selectedOpen = false;
    },
    mouseMove(tms) {
      if (this.newEventStarted) {
        const mouse = this.toTime(tms);
        this.event.endDate = this.roundTime(mouse);
        this.retrieveEvents();
      }
    },
    endDrag() {
      if (this.newEventStarted) {
        this.newEventStarted = false;
        this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
        this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);
        this.selectedOpen = false;
        this.selectedEvent = null;
        this.$nextTick().then(() => {
          this.retrieveEvents();
          this.showEventDatePickers(this.event);
        });
      }
    },
    getEventDomId(eventObj) {
      const event = eventObj && eventObj.event || eventObj;
      return `eventForm-${event.id}-${new Date(event.startDate).getTime()}`;
    },
    getEventColor(event) {
      return event && (event.color || event.calendar && event.calendar.color) || '#2196F3';
    },
    isEventTimed(event) {
      return event && !event.allDay;
    },
    nextDate() {
      this.$refs.calendar.next();
    },
    prevDate() {
      this.$refs.calendar.prev();
    },
    retrieveEvents(range) {
      this.displayedEvents = this.events.slice();
      if (range) {
        this.retrievePeriod(range);
      }
      this.$forceUpdate();
    },
    retrievePeriod(range) {
      const period = this.$agendaUtils.convertVuetifyRangeToPeriod(range, this.$userTimeZone);
      if (period) {
        period.title = this.$refs.calendar.title;
        this.periodTitle = this.$agendaUtils.generateCalendarTitle('week', new Date(period.start), period.title, this.$t('agenda.week'));
      }
    },
    roundTime(time, down = true) {
      const roundTo = 15; // minutes
      const roundDownTime = roundTo * 60 * 1000;

      return down
        ? time - time % roundDownTime
        : time + (roundDownTime - time % roundDownTime);
    },
    toTime(tms) {
      return new Date(tms.year, tms.month - 1, tms.day, tms.hour, tms.minute).getTime();
    },
    agendaIntervalStyle(interval) {
      if (this.workingTime.showWorkingTime) {
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
      } else {
        return null;
      }
    },
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connected-account-settings-open');
    }
  },
};
</script>
