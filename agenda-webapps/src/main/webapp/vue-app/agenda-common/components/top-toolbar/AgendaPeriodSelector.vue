<template>
  <div class="d-flex">
    <v-btn
      max-width="36"
      max-height="36"
      icon
      @click="prevDate">
      <v-icon size="20" class="text-light-color">
        fa-chevron-left
      </v-icon>
    </v-btn>
    <v-btn
      max-width="36"
      max-height="36"
      icon
      @click="nextDate">
      <v-icon size="20" class="text-light-color">
        fa-chevron-right
      </v-icon>
    </v-btn>
    <div class="period-title text-uppercase my-auto ms-2">
      {{ periodTitle }}
    </div>
  </div>
</template>
<script>
export default {
  props: {
    periodTitle: {
      type: String,
      default: null
    },
  },
  methods: {
    nextDate() {
      this.$root.$emit('agenda-display-calendar-next');
    },
    prevDate() {
      this.$root.$emit('agenda-display-calendar-previous');
    }
  },  
};
</script>
