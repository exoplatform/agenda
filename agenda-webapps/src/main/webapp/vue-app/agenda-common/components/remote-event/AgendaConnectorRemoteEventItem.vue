<template>
  <div
    :class="{
      'no-date-event' : !displayEventDate,
      'primary': currentEvent
    }"
    class="v-event-draggable remote-event rounded v-event-draggable-parent">
    <p
      :title="remoteEvent.summary"
      :class="textClass"
      class="text-truncate-2 my-auto ms-2 caption font-weight-bold">
      {{ remoteEvent.summary }}
    </p>
    <template v-if="!displayEventDate">
      <agenda-connector-avatar
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
        v-if="!currentEvent"
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