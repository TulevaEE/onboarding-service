# onboarding-service
Onboarding Service

## Rest API Proposal
O-Auth2 based on Mobile-ID and ID-card authentication provider auth calls.

//tuleva offered funds  
**/available-pension-funds**
```js
[
  {
    id: 1,
    isin: "AE12324344336",
    name: "STOCK",
    price: 0.3,
    pillar: 2
  }
]
```

//current funds in EVK  
**/pension-account-statement**
```js
[
  {
    id: 1,
    name: "LHV XL", //PF_NIMETUS
    isin: "AE233242342",
    manager: "LHV",
    shares: 10000,
    price: 0.1,
    currency: "EUR",
    pillar: 2
  }
]
```

POST **/exchange-applications**  
Body:
```js
{
  funds: [
    {
      source: "AE43434334", //isin
      target: "AE43433434", //isin
      sharePercentage: 100
    }
  ]
}
```
Response:
```js
{
  id: 123,
  ...
}
```

POST **/selection-applications**  
Body:
```js
{
  fund: "AE434334344" //isin
}
```
Response:
```js
{
  id: 123,
  ...
}
```

**/users/{id}**
```js
{
  id: 123,
  firstName: "Jordan",
  lastName: "Valdma",
  personalIdCode: "38812022762",
  created: "2017-01-29 20:13:44",
  memberNumber: 1234 // or null if not a member
}
```

**/initial-capital**
```js
{
  amount: 3000,
  currency: "EUR"
}
```

**/orders/{id}**
```js
{
  id: 0, //starting capital, because id is 0
  fund: 1,
  amount: 1000,
  currency: "EUR",
  price: 0.3,
  transaction: "BUY",
  created: "2017-01-29 20:13:44"
}
```

//available for public  
**/comparisons/?totalCapital=1000&age=30&monthlyWage=2000&isin=AE233242342**
```js
[
  {
    //fundName: "LHV XL",
    isin: "AE233242342",
    totalFee: 51546.56,
    currency: "EUR"
  }
]
```

**/savings**
```js
{
  amount: 123
}
```
