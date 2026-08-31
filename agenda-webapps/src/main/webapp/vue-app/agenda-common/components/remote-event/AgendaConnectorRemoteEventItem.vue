<template>
  <div
    :class="{
      'no-date-event' : !displayEventDate,
      'primary': currentEvent
    }"
    :style="calendarRailStyle"
    class="v-event-draggable remote-event rounded v-event-draggable-parent">
    <p
      :title="rowTitle"
      :class="textClass"
      class="text-truncate-2 my-auto ms-2 caption font-weight-bold">
      {{ remoteEvent.summary }}
    </p>
    <template v-if="!displayEventDate">
      <agenda-connector-avatar
        v-if="connector"
        :connector="connector"
        class="me-1 my-auto"
        size="16" />
    </template>
    <div v-if="displayEventDate" class="d-flex">
      <date-format
        :value="remoteEvent.start || remoteEvent.startDate"
        :format="timeFormat"
        :class="textClass"
        class="v-event-draggable ms-2" />
      <strong :class="textClass" class="mx-1">-</strong>
      <date-format
        :value="remoteEvent.end || remoteEvent.endDate"
        :format="timeFormat"
        :class="textClass"
        class="v-event-draggable me-2" />
      <agenda-connector-avatar
        v-if="connector && !currentEvent"
        :connector="connector"
        class="white ms-auto me-1"
        size="16" />
    </div>
  </div>
</template>

<script>
export default {
  props: {
    remoteEvent: {
      type: Object,
      default: () => ({})
    },
    event: {
      type: Object,
      default: () => null
    },
    connector: {
      type: Object,
      default: null
    },
    isEventsList: {
      type: Boolean,
      default: false
    },
    /*
     * What the row says on hover. Left empty by the surfaces that have
     * nothing to add, which then keeps the event's own title.
     */
    hoverTitle: {
      type: String,
      default: ''
    },
  },
  data() {
    return {
      timeFormat: {
        hour: '2-digit',
        minute: '2-digit',
      },
    };
  },
  computed: {
    /**
     * The row's hover text: what the surface hosting the row asked for, else
     * the event's own title.
     *
     * @returns {String} the title attribute of the row
     */
    rowTitle() {
      return this.hoverTitle || this.remoteEvent.summary;
    },
    /**
     * The colour of the calendar this row's event lives in, on the surfaces
     * that list events from several calendars at once.
     *
     * <p>
     * Never on the current event: that row is a solid block marking where the
     * event being looked at falls among the others, and a second colour on it
     * would compete with the one thing the list has to anchor.
     *
     * <p>
     * Only where the rows are a list. The calendar grid draws this same
     * colour on the `.v-event` around this component already, and a second
     * rail inside the first is not more information.
     *
     * @returns {String} the hex colour, empty when this row shows none
     */
    calendarRailColor() {
      if (!this.isEventsList || this.currentEvent) {
        return '';
      }
      return this.$agendaUtils.calendarColor(this.remoteEvent);
    },
    /**
     * The rail itself: a thin left edge in the calendar's colour, which
     * reinforces what the row's hover text already says rather than carrying
     * it alone.
     *
     * <p>
     * A row with no colour to show keeps the rail transparent rather than
     * dropping it. Drawing nothing would pull its text 4px left of every
     * other row, and drawing a neutral grey would say "this calendar is grey"
     * — an answer where there is none.
     *
     * @returns {Object} the style binding, empty off the list surfaces
     */
    calendarRailStyle() {
      if (!this.isEventsList) {
        return {};
      }
      return {borderLeft: `4px solid ${this.calendarRailColor || 'transparent'}`};
    },
    currentEvent() {
      return this.event && this.event.id === this.remoteEvent.id;
    },
    textClass() {
      return this.currentEvent ? 'white-text':'primary--text';
    },
    isShortEvent() {
      return this.$agendaUtils.isShortEvent(this.remoteEvent);
    },
    displayEventDate() {
      return (!this.remoteEvent.allDay && !this.isShortEvent) || (this.isEventsList && !this.remoteEvent.allDay);
    }
  }
};
</script>