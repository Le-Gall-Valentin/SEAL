import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { conversationFailureOf } from './conversationFailure'

describe('entities/conversation : refus des conversations (14.6, ADR 0041 point 4)', () => {
  it.each([
    [new ApiError(403, 'NOT_FRIENDS'), { kind: 'not-friends' }],
    [new ApiError(409, 'GROUP_FULL'), { kind: 'group-full' }],
    [new ApiError(403, 'FORBIDDEN'), { kind: 'forbidden' }],
    [new ApiError(404, 'NOT_FOUND'), { kind: 'gone' }],
    [new ApiError(400, 'VALIDATION_FAILED', { fields: ['name'] }), { kind: 'invalid-name' }],
    [new ApiError(400, 'VALIDATION_FAILED', { fields: ['memberIds'] }), { kind: 'unexpected' }],
    [new ApiError(404, 'NOT_A_MEMBER'), { kind: 'unexpected' }],
    [new NetworkError(), { kind: 'network' }],
    [new ApiError(500, 'INTERNAL_ERROR'), { kind: 'unexpected' }],
  ])('CNV-01, CNV-03, GRP-03 : %s', (error, expected) => {
    expect(conversationFailureOf(error)).toEqual(expected)
  })
})
