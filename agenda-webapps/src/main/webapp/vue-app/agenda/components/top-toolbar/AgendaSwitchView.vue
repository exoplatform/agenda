<template>
  <v-tabs
    v-model="agendaPeriodType"
    align-with-title
    centered
    class="text-capitalize">
    <v-tab href="#day" dark>
      {{ $t('agenda.label.viewDay') }}
    </v-tab>
    <v-tab href="#week" dark>
      {{ $t('agenda.label.viewWeek') }}
    </v-tab>
    <v-tab href="#month" dark>
      {{ $t('agenda.label.viewMonth') }}
    </v-tab>
  </v-tabs>
</template>
<script>
export default {
  props: {
    agendaPeriodType: {
      type: String,
      default: null
    },
  },
  watch: {
    agendaPeriodType(newValue, oldValue) {
      if (oldValue) {
        this.$root.$emit('agenda-change-period-type', this.agendaPeriodType);
      }
    },
  },
};
</script>
