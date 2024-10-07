<template>
  <div>
    <div class="d-flex justify-center">
      <v-btn outlined color="primary"
        class="pl-2 pr-2 text-body-1 btn border-radius v-chip v-chip--outlined theme--light primary primary--text mt-4 mb-2"
        :aria-label="$t('agenda.icsbutton')"
        :title="$t('agenda.icsbutton')"
        @click="downloadICS">
        <v-icon class="uiIcon20x20 pe-3">
          fa-calendar-plus
        </v-icon>
        {{ $t('agenda.icsbutton') }}
      </v-btn>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    connectors: {
      type: Array,
      default: () => null
    },
    event: {
      type: Object,
      default: () => ({})
    },
  },
  methods: {
    downloadICS() {
      this.$emit('download-ics', this.event);
    },
  }
};
</script>
