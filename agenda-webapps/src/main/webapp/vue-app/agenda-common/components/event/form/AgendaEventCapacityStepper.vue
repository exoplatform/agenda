<template>
  <div class="d-flex flex-column">
    <div class="d-flex align-center">
      <span class="text-color subtitle-2 font-weight-bold me-auto">{{ $t('agenda.capacity.label') }}</span>
      <v-btn
        :disabled="!value"
        :title="$t('agenda.capacity.decrease')"
        icon
        small
        @click="decrease">
        <v-icon size="16" class="icon-default-color">fas fa-minus</v-icon>
      </v-btn>
      <span class="text-color mx-2 text-no-wrap">{{ value ? value : $t('agenda.capacity.noLimit') }}</span>
      <v-btn
        :title="$t('agenda.capacity.increase')"
        icon
        small
        @click="increase">
        <v-icon size="16" class="icon-default-color">fas fa-plus</v-icon>
      </v-btn>
    </div>
    <div v-if="atMinimum" class="d-flex align-center caption text-sub-title">
      <v-icon size="14" class="info--text me-1">fas fa-info-circle</v-icon>
      <span>{{ $t('agenda.capacity.minimumHint') }}</span>
    </div>
  </div>
</template>

<script>
/**
 * The limit of an open event, 0 standing for no limit. It never goes below the
 * participants who said Yes: from no limit, the first value is their count,
 * and one step below it is no limit again. The server holds the same floor and
 * refuses a lower value, which this only spares the organiser.
 */
export default {
  props: {
    value: {
      type: Number,
      default: 0,
    },
    acceptedCount: {
      type: Number,
      default: 0,
    },
  },
  computed: {
    minimum() {
      return Math.max(this.acceptedCount, 1);
    },
    atMinimum() {
      return this.acceptedCount > 0 && this.value === this.acceptedCount;
    },
  },
  methods: {
    increase() {
      this.$emit('input', this.value ? this.value + 1 : this.minimum);
    },
    decrease() {
      this.$emit('input', this.value > this.minimum ? this.value - 1 : 0);
    },
  },
};
</script>
