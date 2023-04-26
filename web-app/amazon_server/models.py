from django.db import models
from django.contrib.auth.models import User
# Create your models here.

class UserProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE)
    is_seller = models.BooleanField(default=False)

    def __str__(self):
        return self.user.username

    class Meta:
        db_table = 'userprofile'

class WareHouse(models.Model):
    x_cord = models.IntegerField()
    y_cord = models.IntegerField()

    def __str__(self):
        return f"<{self.x_cord}, {self.y_cord}>"
    
    class Meta:
        db_table = 'warehouse'

class Item(models.Model):
    description = models.CharField(max_length=100)
    def __str__(self):
        return self.description
    class Meta:
        db_table = 'item'

class Order(models.Model):
    order_id = models.AutoField(primary_key=True)
    warehouse = models.ForeignKey(WareHouse, on_delete=models.CASCADE)
    buyer = models.ForeignKey(User, on_delete=models.CASCADE, related_name="orders")
    status = models.CharField(max_length=100)
    dest_x = models.IntegerField()
    dest_y = models.IntegerField()
    package_id = models.BigIntegerField(null=True, blank=True)  # added field
    ups_account_name = models.CharField(max_length=255, null=True, blank=True)  # added field

    def __str__(self):
        return f"<order_id: {self.order_id}, buyer_id: {self.buyer.id}, status: {self.status}>"

    class Meta:
        db_table = 'order'


class Ordered_Items(models.Model):
    item = models.ForeignKey(Item, on_delete=models.CASCADE)
    count = models.IntegerField()
    order = models.ForeignKey(Order, on_delete=models.CASCADE)

    def __str__(self):
        return f"<order_id: {self.order_id}, item_id: {self.item.id}, count: {self.count}>"
    
    class Meta:
        db_table = 'ordered_items'

class ShoppingCartItem(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    item = models.ForeignKey(Item, on_delete=models.CASCADE)
    quantity = models.PositiveIntegerField()

    class Meta:
        unique_together = ('user', 'item')
        db_table = 'shoppingcartitem'

class Comment(models.Model):
    item = models.ForeignKey(Item, on_delete=models.CASCADE)
    buyer = models.ForeignKey(User, on_delete=models.CASCADE)
    content = models.TextField()

    def __str__(self):
        return f"<Comment by {self.buyer} on {self.item}>"

    class Meta:
        db_table = 'comment'
