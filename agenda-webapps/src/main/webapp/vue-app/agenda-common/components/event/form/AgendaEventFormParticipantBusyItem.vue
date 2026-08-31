<template>
  <!-- The ordinary event's own container and class list, with the avatar
       inserted as the leading element of the title line. Not "close to" it —
       the same `.readonly-event` root and the same
       `text-truncate my-auto caption font-weight-bold d-flex` title, so the
       two blocks in this grid cannot drift apart by hand-tuning. See
       AgendaEventFormDates.vue's own #event slot for the block this mirrors.

       WHAT WAS WRONG: `d-flex align-center` on the root. In this skin the
       align-center utility ALSO sets text-align: center — a trap agenda.less
       already documents twice, on .agenda-left-panel-title and on
       .agenda-mirror-calendar-choice — so the block was centred on BOTH axes
       while every other block in the grid is anchored top-left. Removing it
       fixes both at once, and nothing replaces it: the ordinary event carries
       no align utility either, and a block that simply stacks its children
       starts at the top by itself.

       INDENT: the title line takes ms-1 (4px) and exo-user-avatar's own
       wrapper adds mx-1 (4px, from its `parentClass`), so the avatar's left
       edge lands on 8px — the ms-2 the ordinary event's title uses. The time
       row keeps that ms-2 directly, so both lines start on the same edge. The
       arithmetic is written down here because it is the one place these two
       blocks are aligned by two numbers rather than by one shared class. -->
  <div class="readonly-event">
    <p class="text-truncate my-auto ms-1 caption font-weight-bold d-flex">
      <exo-user-avatar
        v-if="identity"
        :identity="identity"
        :size="16"
        :popover="false"
        :url="false"
        avatar />
      <span class="text-truncate">{{ $t('agenda.busy') }}</span>
    </p>
    <!-- Hidden on a short block by the same isShortEvent test the ordinary
         event uses, so a 30-minute block shows one line in both cases rather
         than overflowing its own height. -->
    <div v-if="!isShortEvent" class="d-flex">
      <date-format
        :value="busyEvent.startDate"
        :format="timeFormat"
        class="ms-2" />
      <strong class="mx-1">-</strong>
      <date-format
        :value="busyEvent.endDate"
        :format="timeFormat"
        class="me-2" />
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
