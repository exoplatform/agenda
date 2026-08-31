<template>
  <!-- The ordinary event's own container and layout classes, with the avatar
       as the leading element of the title line. See AgendaEventFormDates.vue's
       #event slot for the block this mirrors.

       TWO DELIBERATE DIVERGENCES FROM THAT BLOCK, BOTH BECAUSE ITS CLASSES
       CARRY ASSUMPTIONS AN AVATAR BREAKS. Each was checked against the
       COMPILED skin (agenda-webapps/target/src/main/webapp/skin/less), not
       against the Vuetify docs, because trusting the docs is what cost the
       previous round.

       1. The title line is a <div>, not a <p> with `my-auto`. `my-auto`
          compiles to `margin-top: auto; margin-bottom: auto !important`
          (vuetify.less:4132) — margins whose computed value DEPENDS on the
          formatting context and on how much room is left: zero in a block
          context, free-space distribution in a flex one. On the ordinary
          block it is there to cancel the <p> element's own default margin,
          and it is scoped `.v-application .my-auto`, so it only cancels it
          while that ancestor is present. A line that must be pinned to the
          top of its block should not depend on either. A <div> has no default
          margin to cancel, so nothing has to be undone and nothing can be
          distributed.

       2. `align-self-center` on both children. `.d-flex` sets `display: flex`
          and nothing else (vuetify.less:3669), so `align-items` stays at the
          CSS default `stretch`: the avatar's wrapper stretches to the line
          height while `v-avatar` keeps its fixed 16px inside it, and the
          label sits on its own baseline — so the two never share a centre
          line. `align-self-center` compiles to `align-self: center !important`
          and NOTHING else (vuetify.less:3813), which is the whole reason it is
          used here instead of the container-level `align-center`.

       NEVER `align-center` ON THIS COMPONENT. It resolves to TWO rules:
       `align-items: center !important` (vuetify.less:3765) AND
       `text-align: center` (the platform's core/helpers.less:723). The second
       is what centred this block horizontally in the first place; agenda.less
       documents the same trap twice, on .agenda-left-panel-title and
       .agenda-mirror-calendar-choice. A pin fails if the token reappears
       anywhere in this file.

       INDENT: the title line takes ms-1 (4px) and exo-user-avatar's own
       wrapper adds mx-1 (4px, from its parentClass), landing the avatar's left
       edge on the 8px the ordinary title gets from ms-2. The time row takes
       that ms-2 directly, so both lines start on the same edge. -->
  <div class="readonly-event">
    <div class="text-truncate ms-1 caption font-weight-bold d-flex">
      <exo-user-avatar
        v-if="identity"
        :identity="identity"
        :size="16"
        :popover="false"
        :url="false"
        avatar
        class="align-self-center" />
      <span class="text-truncate align-self-center">{{ $t('agenda.busy') }}</span>
    </div>
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
