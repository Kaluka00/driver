<template>
	<view>
		<view class="waiting-container">
			<image src="../static/waiting_payment/payment.png" mode="widthFix" class="payment"></image>
			<view class="title">等待顾客付款</view>
			<view class="second">{{ i }}s</view>
			<button class="btn" @tap="checkPaymentHandle">未收到付款通知</button>
		</view>

		<view class="notice-container">
			<view class="notice-title">
				<u-icon name="error-circle-fill" top="2" color="#fea802" size="36"></u-icon>
				<text>注意事项</text>
			</view>
			<view class="desc">
				<text class="num">1.</text>
				如果顾客已经成功付款，但是司机端未能收到付款成功的通知消息，可以点击“未收到顾客付款通知”的链接。系统将立即向微信支付平台查询顾客的付款结果；
			</view>
			<view class="desc">
				<text class="num">2.</text>
				如果顾客已经成功付款，并且您的微信上面也收到了分账通知消息，但是本页面并没有接收到用户付款成功的通知消息。您可以点击已经收款按钮，系统将立即确认付款结果；
			</view>
			<view class="desc">
				<text class="num">3.</text>
				如果顾客已经成功付款，并且本页面也接收到了付款成功的通知，但是您没有收到系统分账的通知。您可以耐心等待，因为本系统将会审核您在代驾中是否存在违规，并且扣除相关罚款，再给您分账。所以在顾客付款成功之后的2小时之内，您会收到本次代驾的分账收入，请您耐心等待，如有技术问题，请拨打400-264166678
			</view>
		</view>
		<u-top-tips ref="uTips"></u-top-tips>
	</view>
</template>

<script>
export default {
	data() {
		return {
			i: 0,
			orderId: null,
			timer: null
		};
	},
	methods: {
		checkPaymentHandle: function() {
			let that = this;
			let data = {
				orderId: that.orderId
			};
			that.ajax(that.url.updateOrderAboutPayment, 'POST', data, function(resp) {
				let result = resp.data.result;
				if (result == '付款成功') {
					uni.showToast({
						title: '客户已付款'
					});
					uni.setStorageSync('workStatus', '停止接单');
					clearInterval(that.timer);
					that.i = 0;
					setTimeout(function() {
						uni.switchTab({
							url: '../../pages/workbench/workbench'
						});
					}, 2500);
				} else {
					uni.showToast({
						icon: '未检测到成功付款'
					});
				}
			});
		}
	},
	onLoad: function(options) {
		let that = this;
		that.orderId = options.orderId;
		that.timer = setInterval(function() {
			that.i++;
			if (that.i % 2 == 0) {
				let data = {
					orderId: that.orderId
				};
				that.ajax(
					that.url.searchOrderStatus,
					'POST',
					data,
					function(resp) {
						if (!resp.data.hasOwnProperty('result')) {
							uni.showToast({
						 	icon: 'none',
								title: '没有找到订单'
							});
							clearInterval(that.timer);
							that.i = 0;
						} else {
							let result = resp.data.result;
							if (result == 7 || result == 8) {
								uni.showToast({
									title: '客户已付款'
								});
								uni.setStorageSync('workStatus', '停止接单');
								clearInterval(that.timer);
								that.i = 0;
								setTimeout(function() {
									uni.switchTab({
										url: '../../pages/workbench/workbench'
									});
								}, 2500);
							}
						}
					},
					false
				);
			}
		}, 1000);
	},
	onShow: function() {},
	onHide: function() {}
};
</script>

<style lang="less">
@import url('waiting_payment.less');
</style>
