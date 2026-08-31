<template>
  <div class="d-flex flex-row align-center readonly-event px-1">
    <exo-user-avatar
      v-if="identity"
      :identity="identity"
      :size="16"
      :popover="false"
      :url="false"
      avatar
      class="flex-shrink-0" />
    <div class="d-flex flex-column overflow-hidden ms-1">
      <span class="caption font-weight-bold text-truncate">{{ $t('agenda.busy') }}</span>
      <div v-if="!isShortEvent" class="d-flex caption">
        <date-format :value="busyEvent.startDate" :format="timeFormat" />
        <strong class="mx-1">-</strong>
        <date-format :value="busyEvent.endDate" :format="timeFormat" />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    /*
     * One busy range of one participant, as the grid holds it. It carries the
     * person and two instants and nothing else, because that is all the server
     * ever sent: no title, no location, no calendar name. The template can
     * therefore not leak event content even by accident — there is none to
     * leak.
     */
    busyEvent: {
      type: Object,
      default: () => ({}),
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
     * The participant this block belongs to, in the shape the shared avatar
     * component reads — the profile the attendee list already holds, so no
     * extra profile call is made to paint the grid.
     *
     * @returns {Object} the participant's profile, null when unknown
     */
    identity() {
      return this.busyEvent && this.busyEvent.identity && this.busyEvent.identity.profile || null;
    },
    /**
     * Whether the block is too short to show its times without overflowing.
     *
     * @returns {Boolean} true when only the avatar and the label fit
     */
    isShortEvent() {
      return this.$agendaUtils.isShortEvent(this.busyEvent);
    },
  },
};
</script>
