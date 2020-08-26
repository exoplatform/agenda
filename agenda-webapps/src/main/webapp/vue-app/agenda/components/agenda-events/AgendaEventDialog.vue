<template>
  <v-dialog
    v-model="showDialogEvent"
    fullscreen
    hide-overlay
    transition="dialog-bottom-transition">
    <v-card>
      <create-event-stepper :show-stepper-event="showDialogEvent" />
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  props: {
    showDialogEvent: {
      type:Boolean,
      default:() => false,
    },
  },
  data () {
    return {
    }
  },
}
</script>