from django import forms
from .models import Item, Comment
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth.models import User

class BecomeSellerForm(forms.Form):
    confirm = forms.BooleanField(
        required=True,
        initial=False,
        label="I want to become a seller"
    )

class AddItemForm(forms.Form):
    description = forms.CharField(
        label="Item Description",
        max_length=100,
        widget=forms.TextInput(attrs={'class': 'form-control'})
    )

class PurchaseForm(forms.Form):
    x_cord = forms.IntegerField(label='X Coordinate', min_value=0)
    y_cord = forms.IntegerField(label='Y Coordinate', min_value=0)
    count = forms.IntegerField(label='Quantity', min_value=1)

class MultiPurchaseForm(forms.Form):
    x_cord = forms.IntegerField(label="X Coordinate", min_value=0)
    y_cord = forms.IntegerField(label="Y Coordinate", min_value=0)
    ups_account_id = forms.CharField(label="UPS Account Id", required=False) 

    def __init__(self, *args, **kwargs):
        selected_items = kwargs.pop('selected_items', [])
        super(MultiPurchaseForm, self).__init__(*args, **kwargs)
        for item_id in selected_items:
            item = Item.objects.get(id=item_id)
            self.fields[f'count_{item_id}'] = forms.IntegerField(
                label=f'Quantity for {item.description}', min_value=1
            )


class ConfirmOrderForm(forms.Form):
    x_cord = forms.IntegerField(label="X Coordinate", min_value=0)
    y_cord = forms.IntegerField(label="Y Coordinate", min_value=0)
    ups_account_id = forms.CharField(label="UPS Account Id", required=False) 

class CommentForm(forms.ModelForm):
    class Meta:
        model = Comment
        fields = ['content']

class RegisterForm(UserCreationForm):
    first_name = forms.CharField(max_length=40)
    last_name = forms.CharField(max_length=40)
    class Meta(UserCreationForm.Meta):
        model = User
        fields = ["username", "first_name", "last_name", "email", "password1", "password2"]